# gear4jtest-external-api

`gear4jtest-external-api` contains the contracts and infrastructure used to load pipeline definitions from external artifacts.

It is the common layer for XML today and potentially JSON, YAML or other formats later.

## Responsibilities

This module owns:

- external artifact storage abstractions;
- in-memory, filesystem and database artifact stores;
- pipeline object/config repositories;
- translator discovery through `OperationChainTranslator`;
- JDT-based in-memory Java compilation;
- generated-class loading and caching;
- dependency injection into generated classes;
- TEST/RUN publication mode handling.

It should not contain XML-specific parsing or generation logic. Format-specific modules should implement `OperationChainTranslator` and register themselves through `ServiceLoader` or explicit injection.

## Main flow

`AssemblyLineManager` is the orchestration entrypoint for external definitions.

Typical flow:

1. Register an artifact for an assembly line id, version and execution mode.
2. Store the raw content in the configured `ArtifactStore`.
3. Store metadata in the object repository.
4. Resolve a translator based on media type.
5. Translate external content into Java source.
6. Compile Java source with `JDTInMemoryCompiler`.
7. Load compiled classes through an `InMemoryClassLoader`.
8. Instantiate a `GeneratedAssemblyLine` with a no-arg constructor.
9. Inject dependencies through `DependencyInjector`.
10. Cache and return the generated assembly line.

## Important types

| Type | Purpose |
| --- | --- |
| `AssemblyLineManager` | Main facade for registering, promoting, loading and compiling external pipelines. |
| `OperationChainTranslator` | SPI implemented by external format modules. |
| `OperationChainTranslatorResolver` | Resolves translators explicitly or with `ServiceLoader`. |
| `GeneratedAssemblyLine` | Interface implemented by generated pipeline classes. |
| `JDTInMemoryCompiler` | Compiles generated Java source without writing class files to disk. |
| `ClassLoaderRegistry` | Tracks generated classloaders and aliases. |
| `DependencyInjector` | Injects external dependencies into generated pipeline instances. |
| `ArtifactStore` | Stores raw external pipeline artifacts by content hash. |

## Publication modes

External definitions use `ExecutionMode`:

- `TEST`: a candidate definition that can be compiled and executed in test mode.
- `RUN`: a runnable definition selected by exact version or latest RUN lookup.

Direct RUN publication is guarded by configuration. The normal flow can publish TEST first and then promote to RUN.

## Dependency injection contract

Generated classes should remain no-arg constructible and implement `GeneratedAssemblyLine`.

Dependencies should be represented as fields annotated with `@Inject`. The manager instantiates the class first, then delegates dependency resolution to the configured `DependencyInjector`.

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

## Testing

Useful focused tasks:

```bash
./gradlew :gear4jtest-external-api:test
./gradlew :gear4jtest-xml:test
```

For translator work, prefer end-to-end tests that translate, compile, instantiate, inject and execute a generated pipeline.
