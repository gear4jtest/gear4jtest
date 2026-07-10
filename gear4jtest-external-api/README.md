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

1. Register an artifact for an assembly line id, version and execution mode.
2. Store the raw content in the configured `ArtifactStore`.
3. Resolve a translator based on media type.
4. Translate external content into Java source.
5. Compile Java source with the configured `GeneratedSourceCompiler`.
6. Store metadata in the object repository only after publication validation succeeds.
7. Load compiled classes through an `InMemoryClassLoader` on demand.
8. Instantiate a `GeneratedAssemblyLine` with a no-arg constructor.
9. Inject dependencies through `DependencyInjector`.
10. Cache and return the generated assembly line.

## Important types

| Type                               | Purpose                                                                           |
|------------------------------------|-----------------------------------------------------------------------------------|
| `AssemblyLineManager`              | Main facade for registering, promoting, loading and compiling external pipelines. |
| `OperationChainTranslator`         | SPI implemented by external format modules.                                       |
| `OperationChainTranslatorResolver` | Resolves translators explicitly or with `ServiceLoader`.                          |
| `GeneratedAssemblyLine`            | Interface implemented by generated pipeline classes.                              |
| `GeneratedSourceCompiler`           | SPI for generated Java compilers.                                                |
| `JavaxToolsGeneratedSourceCompiler` | Default compiler when the runtime provides the JDK `javax.tools.JavaCompiler`.   |
| `JDTInMemoryCompiler`              | Fallback Eclipse JDT compiler for runtimes without `jdk.compiler`.               |
| `ClassLoaderRegistry`              | Tracks generated classloaders and aliases.                                        |
| `DependencyInjector`               | Injects external dependencies into generated pipeline instances.                  |
| `ArtifactStore`                    | Stores raw external pipeline artifacts by content hash; supports bounded streaming writes. |


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
no compiler is injected. The default prefers the standard JDK
`javax.tools.JavaCompiler` and falls back to Eclipse JDT when the runtime image is
stripped and does not include `jdk.compiler`, or when javac cannot compile with
the current runtime classpath. Applications that need deterministic
compiler selection should inject `GeneratedSourceCompilers.javac(...)`,
`GeneratedSourceCompilers.jdt(...)`, or their own `GeneratedSourceCompiler`.

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
Passing `ArtifactStore.UNLIMITED_SIZE` is an explicit trusted-deployment opt-in.

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
