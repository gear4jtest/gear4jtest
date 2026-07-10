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
| `gear4jtest-external-jdbc`   | Optional JDBC repositories, migrations and database artifact implementation.     |
| `gear4jtest-xml`             | XML-specific validation, parsing and Java generation.                            |
| `gear4jtest-gradle-xml2java` | Build-time generation from XML.                                                  |
| `gear4jtest-core`            | Runtime execution after an `AssemblyLine` exists.                                |

## Publication validation

Before TEST or direct RUN metadata is persisted, `AssemblyLineManager` validates the publication candidate by reading the
artifact, resolving the translator, translating the external definition and compiling the generated Java source. Promotion
from TEST to RUN runs the same validation against the RUN candidate before inserting RUN metadata or invalidating the
latest alias.

This validation intentionally stops before instantiating the generated class or injecting application dependencies.
Instantiation can have dependency-container side effects and remains part of the normal runtime loading path. The
publication contract is therefore: the artifact is present, readable, translatable and compilable before it can be stored
as TEST or RUN metadata.

## Runtime loading path

The runtime external loading path is coordinated by `AssemblyLineManager`:

1. locate metadata for the requested assembly line id, version and mode;
2. read the raw artifact from the configured store;
3. resolve a translator by media type;
4. translate the artifact into Java source;
5. compile the Java source in memory;
6. load the compiled classes through an in-memory classloader;
7. instantiate the generated class;
8. inject dependencies allowed for the requested `ExecutionMode`;
9. cache the loaded generated assembly line.

The cache-miss path is single-flight per immutable loader id. Concurrent requests for one artifact therefore share the
same compilation, classloader and generated instance; an unsuccessful flight is evicted to allow a later retry.

## Translator contract

An `OperationChainTranslator` should be format-specific and side-effect light.

It receives bytes and a media type. It returns:

- a fully-qualified generated class name;
- formatted Java source.

Compilation and classloading remain external-api responsibilities.

## Generated class contract

A generated class should:

- implement `GeneratedAssemblyLine<IN, OUT>`;
- have a public no-argument constructor;
- build and return a core `AssemblyLine`;
- receive external dependencies through fields annotated with `@Inject`.

The built-in `SimpleDependencyInjector` treats `registerBean(name, bean)` as RUN-only. TEST-safe dependencies must be
registered with an explicit mode allowlist, for example `registerBean("modelsService", modelsService, ExecutionMode.TEST,
ExecutionMode.RUN)`. Custom injectors should preserve the same principle: TEST and RUN do not automatically share the
same application dependency surface.

## Versioning and aliases

Pinned version references are stable.

Mutable aliases such as `latest` are invalidated at the loader/cache boundary. When a RUN object is published or a TEST
object is promoted to RUN through `AssemblyLineManager`, the `al/<id>/RUN/latest` classloader alias is cleared. The next
latest lookup resolves the current repository state and registers a fresh alias to the concrete compiled loader id.

Pinned version lookups remain stable and are not evicted by alias invalidation. Already-running pipeline graphs are never
mutated; invalidation only affects future latest lookups. This cache invalidation is local to the manager/classloader
registry. Exact-version RUN lookups never update `latest`. The latest lookup captures a local invalidation generation,
so a compilation that began before a publication cannot restore its obsolete alias after publication completes. This
does not provide distributed cache coherence between JVMs.

The core pipeline-call model already supports both:

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
The XML translator discovered through `ServiceLoader` uses the restrictive no-inline-Java policy by default. Trusted
runtime loading must inject an explicitly trusted translator, for example `XmlOperationChainTranslator.trusted()`, instead
of relying on implicit defaults.


- prefer build-time generation for externally maintained definitions when possible;
- keep runtime loading for trusted internal definitions;
- keep `trusted` / inline-Java mode explicit rather than implicit;
- validate generated class names and packages before compilation;
- document that handler code must be reviewed with the same care as handwritten Java operators.

## Artifact size limits

External pipeline artifacts are expected to be small XML/source bundles. The
artifact APIs now expose bounded read helpers (`ArtifactStore.put(InputStream,
maxBytes)` and `ArtifactStore.readAllBytes(...)`) so applications can reject
unexpectedly large inputs before loading them fully in memory.

`ArtifactStore.put(InputStream)` and `AssemblyLineManager` now apply a bounded
default of `ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES` /
`AssemblyLineManager.DEFAULT_MAX_ARTIFACT_SIZE_BYTES` (5 MiB). Composite-store
read verification uses the same default bound, and composite streaming writes
spool through temporary files so synchronous fallbacks do not require a second
full in-memory copy. Applications that intentionally need larger generated
definitions must pass an explicit limit to the advanced manager constructor or
call `ArtifactStore.put(InputStream, maxBytes)` directly.
`ArtifactStore.UNLIMITED_SIZE` remains available only as an explicit opt-in for
trusted deployments.
