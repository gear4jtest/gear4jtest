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

## Trust model for generated Java

External definitions are not a sandbox boundary.

XML parsing is hardened against XXE, but the XML module can still translate a definition into Java source, compile it,
load it into the current JVM and execute it with the same permissions as the hosting application. Inline Java snippets,
generated Java source and dynamically loaded classes must therefore be treated as application code.

Only compile and execute external definitions that come from trusted provenance, such as reviewed source control,
controlled build artifacts or an administration workflow with equivalent review rules. Do not expose runtime compilation
of arbitrary XML or Java snippets to untrusted users.

Using Eclipse JDT or the JDK `JavaCompiler` does not change this security model. A compiler API can change build-time
stability and dependency choices, but it does not sandbox the resulting bytecode.

Operational guidance:

- prefer build-time generation for externally maintained definitions when possible;
- keep runtime loading for trusted internal definitions;
- make any future `trusted` / inline-Java mode explicit rather than implicit;
- validate generated class names and packages before compilation;
- document that handler code must be reviewed with the same care as handwritten Java operators.
