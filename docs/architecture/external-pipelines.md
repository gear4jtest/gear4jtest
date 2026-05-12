# External pipeline architecture

## Status

Partially implemented.

## Intent

External pipeline support allows pipeline definitions to be stored outside Java source code and translated into
executable Gear4J pipelines.

XML is the current implemented external format. Other formats can be added later by implementing the same translator
contract.

## Module split

| Module                       | Responsibility                                                                   |
|------------------------------|----------------------------------------------------------------------------------|
| `gear4jtest-external-api`    | Common artifact, repository, compiler, classloader and injection infrastructure. |
| `gear4jtest-xml`             | XML-specific validation, parsing and Java generation.                            |
| `gear4jtest-gradle-xml2java` | Build-time generation from XML.                                                  |
| `gear4jtest-core`            | Runtime execution after an `AssemblyLine` exists.                                |

## Runtime loading path

The runtime external loading path is coordinated by `AssemblyLineManager`:

1. locate metadata for the requested assembly line id, version and mode;
2. read the raw artifact from the configured store;
3. resolve a translator by media type;
4. translate the artifact into Java source;
5. compile the Java source in memory;
6. load the compiled classes through an in-memory classloader;
7. instantiate the generated class;
8. inject dependencies;
9. cache the loaded generated assembly line.

## Translator contract

An `OperationChainTranslator` should be format-specific and side-effect light.

It receives bytes and a media type. It returns:

- a fully-qualified generated class name;
- formatted Java source.

Compilation and classloading remain external-api responsibilities.

## Generated class contract

A generated class should:

- implement `GeneratedAssemblyLine`;
- have a public no-argument constructor;
- build and return a core `AssemblyLine`;
- receive external dependencies through fields annotated with `@Inject`.

## Versioning and aliases

Pinned version references are stable.

Mutable aliases such as `latest` need future cache invalidation rules. Until that is fully implemented, avoid assuming
that alias invalidation is durable or distributed.

When aliasing is involved, future code should preserve both:

- `declaredReference`: what the external definition asked for;
- `resolvedReference`: the concrete version that was actually loaded.

This is important for BO display, dependency tracking, invalidation and traceability.
