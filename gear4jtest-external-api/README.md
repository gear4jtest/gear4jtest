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

SPI discovery has no classpath-order precedence. Translator ids and compiler
ids must be stable. Duplicate store types, overlapping translators and multiple
compiler providers fail with an ambiguity diagnostic unless the applicable
resolver or factory selects one id explicitly. A translator whose
`supports(...)` probe fails also fails resolution because Gear4J cannot prove
that a remaining provider is unambiguous.

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
| `GeneratedLoadingConfiguration`    | Complete-load deadline, parallelism and bounded queue policy.                      |
| `GeneratedLoadingStats`            | Complete-load outcomes, integrity, phase duration/failure, timeout and saturation counters. |
| `GeneratedLoadingPhase`            | Finite artifact/translation/compilation/classloading/construction/injection phases. |
| `GeneratedLoadingPhaseStats`       | Attempts, failures and cumulative/maximum duration for one loading phase.           |
| `ArtifactStoreResolutionStats`     | Bounded resolver occupancy, churn and provider-lease release counters.              |
| `JavaxToolsGeneratedSourceCompiler` | Default compiler when the runtime provides the JDK `javax.tools.JavaCompiler`.    |
| `GeneratedSourceCompilers.jdt(...)` | Selects the internal Eclipse JDT fallback for runtimes without `jdk.compiler`.   |
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

One publication request accepts at most 64 tag entries, each non-blank and at most 100 characters. Tags are
deduplicated and sorted before staging. Idempotent retries merge tags under the same 64-tag persisted limit. JDBC
repositories batch the stage and committed-tag writes inside the owning transaction.

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
After restart, construct a new `ArtifactPublicationReconciler` over the durable repositories and the store configuration
fingerprinted in the stage. A crash after staging but before the artifact write is resolved by conditional abort after the
grace period. A crash after the artifact write but before commit is resolved by committing the invisible stage. Repeating
the reconciliation pass is idempotent.
Version listing through `OperationChainObjectRepository` always requires a bounded `PageRequest`; there is no unbounded
fallback. Custom repository implementations must apply the page before materializing results.

Maintenance scans use keyset methods in addition to ordinary UI-style pages.
Custom object repositories implement `findAllAfter(...)` ordered by
`publishedAt DESC, id DESC`; staged repositories implement
`findStagedAfter(...)` ordered by `stagedAt, stageId`. Consistency and
reconciliation reports expose `complete()` and `nextCursor()` so applications
can schedule finite continuation passes.

## Compiler SPI

`AssemblyLineManager` accepts a `GeneratedSourceCompiler`, so applications can
replace the default compiler without changing the manager. Built-in options:

```java
GeneratedSourceCompilers.defaultCompiler(classLoader); // javac when available, otherwise JDT
GeneratedSourceCompilers.javac(classLoader);
GeneratedSourceCompilers.jdt(classLoader);
GeneratedSourceCompilers.fromServiceLoader(classLoader);
GeneratedSourceCompilers.fromServiceLoader(classLoader, "company-javac");
```

`AssemblyLineManager` uses `GeneratedSourceCompilers.defaultCompiler(...)` when
no compiler is injected. The backend is selected once when the manager is built:
the standard JDK `javax.tools.JavaCompiler` is used when `jdk.compiler` is
available, otherwise Eclipse JDT is used. A javac source error is returned directly
and is never retried with JDT. The javac implementation resolves file-based URLs
from the supplied parent classloader in addition to the process classpath.
`JDTInMemoryCompiler` is an `@Internal` adapter because Eclipse exposes the
low-level in-memory hooks through implementation packages. Consumer code must
depend on `GeneratedSourceCompiler` and the `GeneratedSourceCompilers` factories;
only the adapter is allowed to import `org.eclipse.jdt.internal.*`.

The manager wraps the selected compiler in a bounded 128-entry/16-MiB, single-flight
cache shared by publication validation and runtime loading. Delegate calls run in
an isolated bounded executor with a 30-second end-to-end deadline by default. The
deadline includes queue wait. One worker is used by default because custom compiler
SPIs are not assumed to be thread-safe; up to 32 distinct compilations may wait.
Identical generated source is therefore compiled once while failures remain
retryable.

Runtime loading has a separate 60-second end-to-end deadline and a bounded
executor with four workers and 32 queue slots by default. This budget starts
before queueing and covers artifact lookup/read, translator resolution and
translation, compilation, class loading, construction and dependency injection.
Every caller joining the same concrete loader ID observes the same remaining
monotonic deadline.

Configure a different finite policy explicitly:

```java
GeneratedCompilationConfiguration compilation =
        GeneratedCompilationConfiguration.defaults()
                .withTimeout(Duration.ofSeconds(10))
                .withMaxConcurrentCompilations(2)
                .withQueueCapacity(16)
                .withMaxGeneratedSourceBytes(2L * 1024L * 1024L)
                .withMaxCompilationOutputBytes(4L * 1024L * 1024L);

GeneratedLoadingConfiguration loading =
        GeneratedLoadingConfiguration.defaults()
                .withTimeout(Duration.ofSeconds(20))
                .withMaxConcurrentLoads(4)
                .withQueueCapacity(16);

AssemblyLineManager manager = AssemblyLineManager.builder()
        // repositories, stores, registry and translator resolver
        .compilationConfiguration(compilation)
        .loadingConfiguration(loading)
        .build();
```

A generated source larger than 4 MiB or a compiler result larger than 8 MiB is
rejected by default with `CompilationLimitExceededException`. These are hard
limits, distinct from the internal 16 MiB completed-compilation cache budget:
oversized bytecode is never returned uncached or passed to a classloader.
`GeneratedCompilationStats.limitRejectedCompilations()` exposes these
rejections.

A deadline breach raises `CompilationTimeoutException`, wakes every caller
sharing that single-flight and removes the flight so a later request can retry.
Cancellation is best-effort: a compiler that ignores interruption may keep its
daemon worker occupied until it returns, but its late result is discarded and is
never cached. Queue saturation fails immediately with `CompilationException`.
`AssemblyLineManager.compilationStats()` exposes cache hits/misses, delegate
duration, timeouts, rejections, active workers and queued work.

A complete-load deadline breach raises
`GeneratedAssemblyLineLoadTimeoutException`. The late worker is interrupted and
its instance is never returned. Registry calls run outside the loading-flight
monitor, so a blocking custom registry cannot postpone the caller deadline. The
single-flight remains reserved until any late registration has been discarded,
which prevents a retry from observing it. Java cannot safely terminate arbitrary
code: an artifact store, translator, constructor, injector or registry that
ignores interruption may keep one daemon worker occupied until it returns.
Definitions that are genuinely hostile must be isolated in another process or
container. `AssemblyLineManager.loadingStats()` exposes complete-load outcomes,
single-flight joins, saturation, artifact-integrity failures and finite
per-phase attempts/failures/durations for artifact reads, translation,
compilation, class loading, construction and injection.

`AssemblyLineManager.storeResolutionStats()` exposes bounded cache occupancy,
hits/misses, configuration replacements, LRU evictions, explicit invalidations
and final provider-lease releases. The snapshot does not expose assembly-line
identifiers, store ids or configuration values.

`AssemblyLineManager` owns both loading and compilation workers and implements
`AutoCloseable`. Long-lived applications must close it during shutdown; tests and
short-lived uses should prefer try-with-resources. Closing the manager also
releases all artifact-store leases retained by its bounded 256-entry store
cache. Store invalidation and manager close must happen after callers using that
manager have quiesced.

`ArtifactStoreProvider.forConfig(...)` returns a store lease. The manager,
consistency checker and publication reconciler return those leases
automatically. Code that uses `DefaultArtifactStoreProvider` directly must call
`release(store)` in a `finally` block or close the provider after all consumers
have stopped. Equivalent store type/property maps share one store while leases
remain active; this keeps `MEMORY` identity coherent between manager operations
and maintenance tools in the same process.

`StoreType` is an open validated value object, not a closed enum. Built-ins keep
the constants `MEMORY`, `FILESYSTEM`, `DATABASE`, `S3` and `SFTP`; a third-party
plugin uses `StoreType.of("COMPANY_STORE")`. The value must match
`[A-Z][A-Z0-9_-]{0,63}` after canonicalization. The same canonical value is used
by `ArtifactStorePlugin.type()`, fallback declarations and JDBC persistence.
Duplicate plugin types fail resolver construction instead of overwriting one
another according to classpath order.

Applications that need deterministic compiler selection should inject
`GeneratedSourceCompilers.javac(...)`, `GeneratedSourceCompilers.jdt(...)`, or
their own `GeneratedSourceCompiler`. Service-loaded applications with exactly
one provider can use the no-id overload; multiple providers must be selected by
their stable `GeneratedSourceCompiler.id()`.

## Classloader lifecycle

`InMemoryClassLoaderRegistry` is bounded by default (`256` concrete loaders and
64 MiB of cumulative generated-bytecode weight). It evicts least-recently-used
unaliased loaders by count or weight and protects aliased loaders so mutable
aliases such as `al/<id>/RUN/latest` never point to a missing loader. A
registration is rejected if protected loaders leave insufficient bytecode
capacity. Applications with high version churn can use
`InMemoryClassLoaderRegistry.builder().maxLoaders(maxLoaders).maxBytecodeWeightBytes(maxBytes).build()`.
If aliases are used for rollback windows or multiple mutable references, use
`InMemoryClassLoaderRegistry.builder().maxLoaders(maxLoaders).maxProtectedLoaders(maxProtectedLoaders).build()` to cap the
number of distinct loaders that aliases may protect from eviction. The registry
also exposes `protectedLoaderCount()`, `isOverCapacityDueToProtectedLoaders()`
and bytecode/rejection values through `snapshotStats()`. Defined class bytes are
removed from the loader's heap map, while their original size remains charged
conservatively because the class still occupies metaspace.
`InMemoryClassLoader.addCompiledClasses(...)` takes a defensive snapshot of
every byte array before registration, so a custom compiler may safely reuse or
clear its output buffers after the call returns.

Custom `ClassLoaderRegistry` implementations must retain the supplied
`RegistrationLease` with each staged entry and keep it invisible until
`isPublished()` becomes true. They must also make
`evictIfOwned(id, expectedLoader)` atomic and idempotent. Generated loading uses
these two guarantees to clean up a registration that returns after its deadline
without exposing it or allowing an old flight to evict a newer loader for the
same identifier.

## Artifact size policy

External definitions are expected to be small source/configuration artifacts.
`ArtifactStore.put(InputStream)`, composite-store verification and
`AssemblyLineManager` enforce a 5 MiB default limit. Use
`ArtifactStore.put(InputStream, maxBytes)`, `verificationMaxArtifactSizeBytes` on
composite artifact-store configuration and the advanced manager constructor with
`maxArtifactSizeBytes` to set a stricter or larger application-specific limit.
Passing `ArtifactStore.UNLIMITED_SIZE` disables only the caller-specific limit;
backend limits still apply.

`InMemoryArtifactStore` additionally defaults to 64 MiB total and 10,000
distinct entries. New content is rejected at capacity; referenced artifacts are
never evicted silently. Configure `maxArtifactSizeBytes`, `maxTotalBytes` and
`maxEntries` on a `MEMORY` store, or use the explicit three-argument constructor,
when a different finite budget has been reviewed.

Built-in artifact-store plugins publish closed property schemas and reject
unknown names. This applies to primary and `fallback.N.props.*` configurations.
Third-party plugins remain open by default; implement `propertySchema()` to opt
into the same typo detection.

## Artifact spool confidentiality

Composite and database stores stage streaming writes in a managed temporary spool. `ArtifactSpoolPolicy` requires
verifiable private permissions by default: Gear4J applies and reads back owner-only POSIX permissions, or an owner-only
ACL when the filesystem exposes an ACL view. Store initialization fails closed when neither mechanism can establish the
invariant.

Configure `spoolDirectory` as a private, application-owned path. On a filesystem whose isolation is enforced outside
the JVM, the explicit `requirePrivatePermissions=false` policy may be used only after the operator has provisioned and
verified equivalent access controls. This opt-out does not make a shared temporary directory safe.

The spool is a bounded temporary workspace, not a recovery queue or write-ahead log. Its `.tmp` files do not encode a
destination or operation, so initialization never replays them. After a crash, recent residues count against the quota;
residues older than `staleFileAge` are deleted on the next initialization. The default 24-hour age is a cleanup retention
period, not a delivery window.

Quota is global to the canonical spool directory across all store instances in
one JVM. Every live store sharing that directory must use identical
`spoolMaxBytes`, `spoolStaleFileAge` and `requirePrivatePermissions` values.
Gear4J holds a private `.gear4j-spool.lock` marker and rejects an explicitly
configured directory already used by another process. Configure a dedicated
directory per process or container. When `spoolDirectory` is omitted, Gear4J
uses a per-JVM subdirectory under the system temporary directory.

For `ASYNC_FALLBACKS`, a successful call guarantees the primary write only. A JVM crash can lose every fallback copy that
has not completed, including a queued copy backed by a spool file. Use `SYNC_ALL` when the call must await fallback writes.
If queued fallback copies must survive a process crash, use externally durable replication or a durable outbox; the
managed spool does not provide that guarantee.

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
