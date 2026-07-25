# gear4jtest-external-api

`gear4jtest-external-api` contains the contracts and infrastructure used to load pipeline definitions from external
artifacts.

It is the common layer for XML today and potentially JSON, YAML or other formats later.

## Responsibilities

This module owns:

- external artifact storage abstractions;
- in-memory and filesystem artifact stores;
- pipeline object/config repositories;
- translator discovery through `OperationChainTranslator`;
- generated-source compiler SPI with JDT and JDK `javax.tools` implementations;
- generated-class loading and caching;
- dependency injection into generated classes;
- TEST/RUN publication mode handling.

It should not contain XML-specific parsing or generation logic. Format-specific modules should implement
`OperationChainTranslator` and register themselves through `ServiceLoader` or explicit injection.

## Main flow

`AssemblyLineManager` is the orchestration entrypoint for external definitions.

Typical flow:

1. Validate identifiers, tags, media type, size and content hash.
2. Resolve a translator, translate the supplied bytes and compile the generated Java before storage.
3. Resolve the exact store configuration and persist an invisible publication stage containing object metadata, tags and
   its configuration fingerprint.
4. Store the raw content in the configured `ArtifactStore`.
5. Atomically commit the staged object and tags.
6. Load compiled classes through an `InMemoryClassLoader` on demand.
7. Instantiate a `GeneratedAssemblyLine` with a no-arg constructor.
8. Inject dependencies through `DependencyInjector`.
9. Cache and return the generated assembly line.

## Important types

| Type                               | Purpose                                                                           |
|------------------------------------|-----------------------------------------------------------------------------------|
| `AssemblyLineManager`              | Main facade for registering, promoting, loading and compiling external pipelines. |
| `OperationChainTranslator`         | SPI implemented by external format modules.                                       |
| `OperationChainTranslatorResolver` | Resolves translators explicitly or with `ServiceLoader`.                          |
| `GeneratedAssemblyLine`            | Interface implemented by generated pipeline classes.                              |
| `GeneratedSourceCompiler`          | SPI for generated Java compilers.                                                 |
| `GeneratedCompilationConfiguration` | Compilation deadline, parallelism and bounded queue policy.                       |
| `GeneratedCompilationStats`        | Cache, duration, timeout and saturation counters.                                 |
| `JavaxToolsGeneratedSourceCompiler` | Default compiler when the runtime provides the JDK `javax.tools.JavaCompiler`.    |
| `JDTInMemoryCompiler`              | Fallback Eclipse JDT compiler for runtimes without `jdk.compiler`.                |
| `ClassLoaderRegistry`              | Tracks generated classloaders and aliases.                                        |
| `DependencyInjector`               | Injects external dependencies into generated pipeline instances.                  |
| `ArtifactStore`                    | Stores raw external pipeline artifacts by content hash; supports bounded streaming writes. |
| `OperationChainPublicationRepository` | Atomic object-and-tags publication capability required by `AssemblyLineManager`.            |
| `InMemoryOperationChainRepository` | Non-durable atomic metadata repository for tests and small single-JVM deployments.          |



## Atomic publication contract

`AssemblyLineManager` requires an `OperationChainPublicationRepository`. The capability may be supplied explicitly with
`publicationRepository(...)` or by an object repository that implements the interface itself. The manager refuses to
build when only independent object and tag repositories are provided, because sequential object-then-tag writes can leave
partial metadata after a failure.

The publication repository must also support the durable staged lifecycle. Stages are not visible through normal
object or tag lookups. If the process fails after metadata staging, `ArtifactPublicationReconciler` checks the configured
store after an operator-selected grace period: it commits stages whose expected content hash exists and conditionally
aborts stages whose artifact is still missing. Idempotent publication retries renew the stage age and revision, preventing
a stale reconciliation pass from deleting an active retry. A changed store configuration retains the stage and reports a
fingerprint mismatch instead of probing a different backend. Store or metadata failures leave the stage available for a
later retry.

This design makes every artifact written by the manager discoverable through durable metadata. It does not retroactively
enumerate or delete store-only artifacts created by older versions or by code that bypasses `AssemblyLineManager`.

For tests and small single-process deployments, one repository can provide all three contracts:

```java
InMemoryOperationChainRepository metadata = new InMemoryOperationChainRepository();

AssemblyLineManager manager = AssemblyLineManager.builder()
        .configRepository(configRepository)
        .objectRepository(metadata)
        .tagRepository(metadata)
        .publicationRepository(metadata)
        .storeProvider(storeProvider)
        .classLoaderRegistry(classLoaderRegistry)
        .translatorResolver(translatorResolver)
        .build();
```

The in-memory implementation is thread-safe and atomic within one JVM, but it is not durable or distributed. JDBC
applications should use `OperationChainObjectRepositoryJdbc`, which implements the publication capability transactionally.

## Compiler SPI

`AssemblyLineManager` accepts a `GeneratedSourceCompiler`, so applications can
replace the default compiler without changing the manager. Built-in options:

```java
GeneratedSourceCompilers.defaultCompiler(classLoader); // javac when available, otherwise JDT
GeneratedSourceCompilers.javac(classLoader);
GeneratedSourceCompilers.jdt(classLoader);
GeneratedSourceCompilers.fromServiceLoader(classLoader);
```

`AssemblyLineManager` uses `GeneratedSourceCompilers.defaultCompiler(...)` when
no compiler is injected. The backend is selected once when the manager is built:
the standard JDK `javax.tools.JavaCompiler` is used when `jdk.compiler` is
available, otherwise Eclipse JDT is used. A javac source error is returned directly
and is never retried with JDT. The javac implementation resolves file-based URLs
from the supplied parent classloader in addition to the process classpath.

The manager wraps the selected compiler in a bounded 128-entry/16-MiB, single-flight
cache shared by publication validation and runtime loading. Delegate calls run in
an isolated bounded executor with a 30-second end-to-end deadline by default. The
deadline includes queue wait. One worker is used by default because custom compiler
SPIs are not assumed to be thread-safe; up to 32 distinct compilations may wait.
Identical generated source is therefore compiled once while failures remain
retryable.

Configure a different finite policy explicitly:

```java
GeneratedCompilationConfiguration compilation =
        GeneratedCompilationConfiguration.defaults()
                .withTimeout(Duration.ofSeconds(10))
                .withMaxConcurrentCompilations(2)
                .withQueueCapacity(16);

AssemblyLineManager manager = AssemblyLineManager.builder()
        // repositories, stores, registry and translator resolver
        .compilationConfiguration(compilation)
        .build();
```

A deadline breach raises `CompilationTimeoutException`, wakes every caller
sharing that single-flight and removes the flight so a later request can retry.
Cancellation is best-effort: a compiler that ignores interruption may keep its
daemon worker occupied until it returns, but its late result is discarded and is
never cached. Queue saturation fails immediately with `CompilationException`.
`AssemblyLineManager.compilationStats()` exposes cache hits/misses, delegate
duration, timeouts, rejections, active workers and queued work.

`AssemblyLineManager` owns these compilation workers and implements
`AutoCloseable`. Long-lived applications must close it during shutdown; tests and
short-lived uses should prefer try-with-resources.

Applications that need deterministic compiler selection should inject
`GeneratedSourceCompilers.javac(...)`, `GeneratedSourceCompilers.jdt(...)`, or
their own `GeneratedSourceCompiler`.

## Classloader lifecycle

`InMemoryClassLoaderRegistry` is bounded by default (`256` concrete loaders). It
evicts least-recently-used unaliased loaders and protects aliased loaders so
mutable aliases such as `al/<id>/RUN/latest` never point to a missing loader.
Applications with high version churn can use `InMemoryClassLoaderRegistry.builder().maxLoaders(maxLoaders).build()`.
If aliases are used for rollback windows or multiple mutable references, use
`InMemoryClassLoaderRegistry.builder().maxLoaders(maxLoaders).maxProtectedLoaders(maxProtectedLoaders).build()` to cap the
number of distinct loaders that aliases may protect from eviction. The registry
also exposes `protectedLoaderCount()` and `isOverCapacityDueToProtectedLoaders()`
for diagnostics.

## Artifact size policy

External definitions are expected to be small source/configuration artifacts.
`ArtifactStore.put(InputStream)`, composite-store verification and
`AssemblyLineManager` enforce a 5 MiB default limit. Use
`ArtifactStore.put(InputStream, maxBytes)`, `verificationMaxArtifactSizeBytes` on
composite artifact-store configuration and the advanced manager constructor with
`maxArtifactSizeBytes` to set a stricter or larger application-specific limit.
Passing `ArtifactStore.UNLIMITED_SIZE` disables only the caller-specific limit;
backend limits still apply.

## Filesystem artifact-store security

The filesystem store normalizes its configured root to an absolute path, rejects
symbolic links anywhere in its existing path, and creates the root plus hash
directories with owner-only POSIX permissions when supported. Artifact files are
published without replacing an existing entry; an existing content-addressed
entry must still match its SHA-256 hash.

Reads reject symbolic links and non-regular files, verify SHA-256, and expose an
in-memory snapshot only after verification succeeds. Filesystem stores enforce
`maxArtifactSizeBytes` on both writes and reads, defaulting to 5 MiB. Set a
larger finite value only when the application heap has been sized accordingly;
an unbounded filesystem store is rejected. Configure the root as a private,
application-owned directory whose parent directories are not writable by
unrelated users. Do not share it with another application or mutate its contents
outside Gear4J. `ArtifactStoreMonitor#snapshotStats()` exposes failed temporary-file
cleanups through `cleanupFailures`.

## Configuration secrecy

`OperationChainConfig.storeProps` is intended for non-sensitive routing and storage options. Its `toString()` exposes
property names for diagnostics but redacts every value. Credentials, tokens and private keys must be resolved by the
application or by a store plugin from its secure configuration source; they must not be embedded in `storeProps`.

## Publication modes

External definitions use `ExecutionMode`:

- `TEST`: a candidate definition that can be compiled and executed in test mode.
- `RUN`: a runnable definition selected by exact version or latest RUN lookup.

Direct RUN publication is guarded by configuration. The normal flow can publish TEST first and then promote to RUN.
Both TEST and RUN publications are validated before metadata insertion by translating and compiling the external artifact.

Latest RUN lookups are cached through a mutable classloader alias named `al/<id>/RUN/latest`. Publishing a RUN object or
promoting a TEST object to RUN through `AssemblyLineManager` clears that alias. The next latest lookup resolves the
repository again and points the alias to the concrete compiled loader id. Exact version loaders remain cached; already
running graphs are not mutated. This is local cache invalidation, not a distributed cross-JVM protocol.

Concurrent cache misses for the same immutable loader id share one compilation and one generated instance. Failed
single-flight entries are removed so a later lookup can retry. Loading an explicit RUN version never rewrites the
`latest` alias. A latest lookup records the local invalidation generation before compiling; if a publication invalidates
the alias in the meantime, the older compilation cannot restore the stale alias.

## Dependency injection contract

Generated classes should remain no-arg constructible and implement `GeneratedAssemblyLine<IN, OUT>`.

Dependencies should be represented as fields annotated with `@Inject`. The manager instantiates the class first, then
delegates dependency resolution to the configured `DependencyInjector`.

`SimpleDependencyInjector.registerBean(name, bean)` exposes a bean to RUN mode only. Beans that are safe for TEST
execution must opt in explicitly with `registerBean(name, bean, ExecutionMode.TEST, ...)`. This prevents draft or
unpromoted external definitions from automatically receiving every application dependency registered in the lightweight
injector.

This keeps generated code compatible with simple classloader-based loading while making the TEST/RUN dependency boundary
explicit.

## Translator contract

A translator must:

- declare whether it supports a media type;
- translate bytes into a fully-qualified generated class name and formatted Java source;
- avoid doing classloading itself;
- keep format-specific parsing outside this module.

## Design boundaries

Keep this module focused on external definition infrastructure.

Do not add:

- XML-specific schema logic;
- Spring-specific lookup behavior;
- durable distributed cache invalidation;
- runtime engine behavior that belongs in `gear4jtest-core`.

## Optional JDBC integration

JDBC repositories, schema migrations and database-backed artifact storage live in the optional `gear4jtest-external-jdbc` module. This keeps the provider-neutral API free from JDBC and Jackson dependencies. Existing pre-1.0 imports under `io.github.gear4jtest.external.api.repository.jdbc` must be migrated as documented in `../gear4jtest-external-jdbc/README.md`.

## Testing

Useful focused tasks:

```bash
./gradlew :gear4jtest-external-api:test
./gradlew :gear4jtest-xml:test
```

For translator work, prefer end-to-end tests that translate, compile, instantiate, inject and execute a generated
pipeline.

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
