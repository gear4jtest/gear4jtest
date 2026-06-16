# gear4jtest-external-api

`gear4jtest-external-api` contains the contracts and infrastructure used to load pipeline definitions from external
artifacts.

It is the common layer for XML today and potentially JSON, YAML or other formats later.

## Responsibilities

This module owns:

- external artifact storage abstractions;
- in-memory, filesystem and database artifact stores;
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
3. Store metadata in the object repository.
4. Resolve a translator based on media type.
5. Translate external content into Java source.
6. Compile Java source with the configured `GeneratedSourceCompiler`.
7. Load compiled classes through an `InMemoryClassLoader`.
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
| `JDTInMemoryCompiler`              | Default Eclipse JDT compiler implementation.                                     |
| `JavaxToolsGeneratedSourceCompiler` | Alternative compiler backed by the JDK `javax.tools.JavaCompiler`.               |
| `ClassLoaderRegistry`              | Tracks generated classloaders and aliases.                                        |
| `DependencyInjector`               | Injects external dependencies into generated pipeline instances.                  |
| `ArtifactStore`                    | Stores raw external pipeline artifacts by content hash; supports bounded streaming writes. |


## Compiler SPI

`AssemblyLineManager` accepts a `GeneratedSourceCompiler`, so applications can
replace the default JDT compiler without changing the manager. Built-in options:

```java
GeneratedSourceCompilers.jdt(classLoader);
GeneratedSourceCompilers.javac(classLoader);
GeneratedSourceCompilers.fromServiceLoader(classLoader);
```

`JavaxToolsGeneratedSourceCompiler` requires a JDK runtime with the standard
`javax.tools.JavaCompiler` available. If an application runs on a stripped runtime
image without `jdk.compiler`, keep using JDT or provide another compiler SPI
implementation.

## Classloader lifecycle

`InMemoryClassLoaderRegistry` is bounded by default (`256` concrete loaders). It
evicts least-recently-used unaliased loaders and protects aliased loaders so
mutable aliases such as `al/<id>/RUN/latest` never point to a missing loader.
Applications with high version churn can use `new InMemoryClassLoaderRegistry(maxLoaders)`.

## Artifact size policy

External definitions are expected to be small source/configuration artifacts.
`ArtifactStore.put(InputStream)`, composite-store verification and
`AssemblyLineManager` enforce a 5 MiB default limit. Use
`ArtifactStore.put(InputStream, maxBytes)` and the advanced manager constructor
with `maxArtifactSizeBytes` to set a stricter or larger application-specific
limit. Passing `ArtifactStore.UNLIMITED_SIZE` is an explicit trusted-deployment
opt-in.

## Publication modes

External definitions use `ExecutionMode`:

- `TEST`: a candidate definition that can be compiled and executed in test mode.
- `RUN`: a runnable definition selected by exact version or latest RUN lookup.

Direct RUN publication is guarded by configuration. The normal flow can publish TEST first and then promote to RUN.

Latest RUN lookups are cached through a mutable classloader alias named `al/<id>/RUN/latest`. Publishing a RUN object or
promoting a TEST object to RUN through `AssemblyLineManager` clears that alias. The next latest lookup resolves the
repository again and points the alias to the concrete compiled loader id. Exact version loaders remain cached; already
running graphs are not mutated. This is local cache invalidation, not a distributed cross-JVM protocol.

## Dependency injection contract

Generated classes should remain no-arg constructible and implement `GeneratedAssemblyLine`.

Dependencies should be represented as fields annotated with `@Inject`. The manager instantiates the class first, then
delegates dependency resolution to the configured `DependencyInjector`.

This keeps generated code compatible with simple classloader-based loading.

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

## JDBC repository support

The built-in JDBC repositories and the database artifact store are provider-scoped rather than a generic
provider-agnostic SQL layer. Supported providers are PostgreSQL, MySQL, MariaDB and Oracle; H2 is also supported for
local and integration testing.

All JDBC entrypoints require the single shared `Gear4jDatabaseDialect` value from `gear4jtest-core`. Gear4J deliberately
does not auto-detect the database through JDBC metadata. For example:

```java
new OperationChainTagRepositoryJdbc(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new DatabaseArtifactStore(dataSource, "artifact_store", Gear4jDatabaseDialect.POSTGRESQL);
```

The `DATABASE` artifact-store plugin similarly requires a `dialect` property such as `POSTGRESQL` or `ORACLE`.
Provider-specific statements such as PostgreSQL `ON CONFLICT`, MySQL/MariaDB `ON DUPLICATE KEY UPDATE` and Oracle/H2
`MERGE` are intentional, but must remain isolated behind `ExternalRepositorySqlDialect`. Adding another provider
requires an enum value, matching schema/migration resources and integration tests for every JDBC-backed component.

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
