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

## Publication validation and staged commit

For TEST and direct RUN publication, `AssemblyLineManager` validates identifiers, tags, media type, size and content hash,
then translates the supplied bytes and compiles the generated Java **before any artifact write**. Promotion from TEST to
RUN validates the already stored TEST artifact before staging RUN metadata or invalidating the latest alias.
Each request is limited to 64 tag entries of at most 100 characters. Normalization is shared by the manager, in-memory
repository and JDBC provider so malformed or oversized tag sets fail before artifact storage or transaction work.

After validation, Gear4J resolves the exact store configuration and computes a stable fingerprint. Publication then
follows a durable three-step protocol:

1. stage object metadata, tags and the store fingerprint in `OperationChainPublicationRepository`;
2. write the content-addressed artifact;
3. atomically commit the stage so object metadata and tags become visible together.

JDBC stage-tag and committed-tag persistence uses one dialect-specific idempotent batch per tag set. A retry locks its
existing stage before merging tags, preserving the persisted 64-tag limit under concurrent retry attempts.

A stage is durable but invisible to normal object and tag repositories. A storage or commit failure therefore cannot leave
a newly written artifact completely unknown to the metadata system. `ArtifactPublicationReconciler` processes stages older
than an operator-selected grace period: it commits a stage when the expected hash exists, conditionally aborts it when the
artifact is missing, and leaves it untouched when the store cannot be checked. An idempotent retry renews the stage age and
revision; a stale reconciliation snapshot cannot abort that renewed stage. If the current store configuration fingerprint
does not match the staged fingerprint, the reconciler retains the stage without probing the new backend. The grace period
must exceed the maximum expected artifact-write duration to avoid racing a legitimate slow upload.

The protocol does not require unsafe deletion from a shared content-addressed store. It also does not retroactively detect
store-only artifacts created before this protocol or written outside `AssemblyLineManager`, because the generic
`ArtifactStore` SPI still cannot enumerate all hashes.

Validation intentionally stops before instantiating the generated class or injecting application dependencies.
Instantiation can have dependency-container side effects and remains part of the normal runtime loading path.

## Runtime loading path

The runtime external loading path is coordinated by `AssemblyLineManager`:

1. locate metadata for the requested assembly line id, version and mode;
2. read the raw artifact from the configured store;
3. resolve a translator by media type;
4. translate the artifact into Java source for the object's `ExecutionMode`;
5. compile the Java source in memory;
6. load the compiled classes through an in-memory classloader;
7. instantiate the generated class;
8. inject dependencies allowed for the requested `ExecutionMode`;
9. cache the loaded generated assembly line.

The cache-miss path is single-flight per immutable loader id. Concurrent requests for one artifact therefore share the
same compilation, classloader and generated instance; an unsuccessful flight is evicted to allow a later retry.
Failure of a custom registry's late-registration cleanup is logged and may leave
an unpublished registry entry for that registry to reclaim, but it does not
retain the runtime single-flight key or prevent a later loading attempt.
Generated-source compilation runs in an isolated bounded executor. Its configurable monotonic deadline includes queue
wait and delegate execution. A timeout completes the shared flight exceptionally, cancels the delegate best-effort and
prevents any late bytecode from entering the completed cache. `AssemblyLineManager` owns this executor, exposes
`GeneratedCompilationStats` for saturation diagnostics and must be closed during application shutdown.

The runtime rejects source above `maxGeneratedSourceBytes` before executor
dispatch and rejects cumulative compiler output above
`maxCompilationOutputBytes` before copying, caching or classloading it. These
hard limits are independent from the completed-cache budget. The default
classloader registry additionally enforces a 64 MiB cumulative bytecode weight;
alias protection may block eviction, but can never authorize an over-budget
registration.

## Translator contract

An `OperationChainTranslator` should be format-specific and side-effect light.

It receives bytes, a media type and, on the publication/runtime path, the
requested `ExecutionMode`. The mode-aware overload defaults to the legacy
two-argument translation method for formats without a mode-dependent capability
surface. It returns:

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

XML parsing is hardened against XXE. Restricted XML additionally rejects inline
Java and requires every `processingOperation/@type` value to resolve through an
exact, mode-aware operator capability allowlist. GEL controls data expressions;
the capability allowlist controls application code invocation. Neither control
makes registered operator implementations a sandbox: generated Java and allowed
operators still execute with the permissions of the hosting application.

Trusted/class-name XML must come from reviewed source control, controlled build
artifacts or an administration workflow with equivalent review rules.
User/BO-authored XML must use GEL plus a restricted
`XmlOperatorCapabilityPolicy`; never use the trusted translator for that path.

Using Eclipse JDT or the JDK `JavaCompiler` does not change this security model. A compiler API can change build-time
stability and dependency choices, but it does not sandbox the resulting bytecode.

Operational guidance:
The XML translator discovered through `ServiceLoader` rejects inline Java and
has an empty operator allowlist. Applications must inject either a configured
`XmlOperationChainTranslator.gelOnly(policy)` or, for reviewed sources only,
`XmlOperationChainTranslator.trusted()`.


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

`CompositeArtifactStore.WriteMode.ASYNC_FALLBACKS` is best-effort replication.
Once the primary store has accepted an artifact, executor rejection or a
fallback write failure is logged and does not turn the completed primary write
into an apparent caller failure. One bounded spool copy and one executor task
are shared by all fallbacks for an artifact, preventing heap/task amplification
as fallback count grows. The default executor has a finite queue and rejects at
saturation; it never converts the asynchronous write into caller-thread I/O.
Use `SYNC_ALL` when the caller must wait for every configured fallback. Stores
are independent, so even synchronous mode cannot provide a cross-store atomic
transaction: a later fallback failure may still occur after an earlier store
accepted the content.
