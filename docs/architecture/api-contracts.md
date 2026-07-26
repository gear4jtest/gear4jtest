# API, SPI and internal contracts

Gear4J is a library, so package boundaries must be explicit before the first
production release. This document defines the current compatibility contract.

## Public API

Public API is intended for application developers that execute or define
pipelines directly.

Current public API packages include:

- `io.github.gear4jtest.core.api.*`, including `AssemblyLineExecutor`, `AssemblyLineExecutors` and read-only trace views
- `io.github.gear4jtest.core.builtin.extension.PersistenceExtension`
- `io.github.gear4jtest.core.event` event contracts and the `EventPublisher` capability used by applications
- `io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions`
- `io.github.gear4jtest.core.sidecompute` side-compute contracts and wait processors
- `io.github.gear4jtest.core.exception` application-visible exception hierarchy
- `io.github.gear4jtest.core.model` application-visible runtime status values
- `io.github.gear4jtest.core.persistence` persistence records, lifecycle and operational-monitoring contracts independent of a storage provider
- `io.github.gear4jtest.jdbc.execution` optional JDBC execution persistence entry points
- `io.github.gear4jtest.jdbc.persistence` optional JDBC repository and dialect entry points
- `io.github.gear4jtest.external.api.AssemblyLineManager`
- `io.github.gear4jtest.external.api.ExecutionMode`
- `io.github.gear4jtest.external.api.GeneratedCompilationConfiguration`
- `io.github.gear4jtest.external.api.GeneratedCompilationStats`
- `io.github.gear4jtest.external.api.StoreType`
- `io.github.gear4jtest.external.api.exception.CompilationLimitExceededException`
- `io.github.gear4jtest.external.api.exception.CompilationTimeoutException`
- `io.github.gear4jtest.external.jdbc.repository.*`
- `io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore`
- `io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy`
- `io.github.gear4jtest.xml.translator.XmlOperationChainTranslator`
- `io.github.gear4jtest.xml.translator.XmlTranslationLimits`
- `io.github.gear4jtest.xml.validator.AssemblyLineValidator`

Compatibility after the first stable release is governed by `docs/compatibility-policy.md` and enforced against the
configured N-1 release with Japicmp. In particular:

- avoid removing public types or methods in patch/minor releases;
- prefer additive changes;
- document behavioral changes in release notes;
- keep exceptions actionable and avoid exposing implementation-only exception
  types as mandatory control flow.

## SPI

SPI is intended for framework/integration authors. Implementations may be
provided by applications or external modules.

Current SPI packages include:

- `io.github.gear4jtest.core.spi.*`
- `io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler`
- `io.github.gear4jtest.external.api.loader.ClassLoaderRegistry`
- `io.github.gear4jtest.external.api.loader.DependencyInjector`
- `io.github.gear4jtest.external.api.spi.*`
- `io.github.gear4jtest.external.api.translator.OperationChainTranslator`
- `io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver`

SPI implementors should assume:

- methods may be called concurrently unless the type explicitly documents
  otherwise;
- generated compiler calls are isolated and serialized by default; applications
  that configure more than one compilation worker are responsible for supplying a
  thread-safe compiler implementation;
- implementations must not retain mutable caller-owned data without copying;
- persistence records validate required identifiers and lifecycle fields;
  persistence managers redact first and clone retained values before asynchronous
  buffering;
- implementations should fail fast with `IllegalArgumentException` for invalid
  configuration and with domain-specific runtime exceptions for operational
  failures;
- blocking operations should document their timeout/cancellation behavior.

## Internal implementation

Everything outside the public API/SPI lists above is internal unless a package
README or Javadoc says otherwise. Internal packages may change without backward
compatibility guarantees before the first production release.

Known internal implementation areas:

- `io.github.gear4jtest.core.engine.*`
- `io.github.gear4jtest.core.execution.*`
- `io.github.gear4jtest.core.event.EventManager` and other members explicitly marked `@Internal`
- `io.github.gear4jtest.core.internal.*`
- `io.github.gear4jtest.core.util.*`
- `io.github.gear4jtest.external.api.storage.*`
- `io.github.gear4jtest.jdbc.migration.*`
- `io.github.gear4jtest.xml.generator.*`
- `io.github.gear4jtest.xml.model.*`
- `io.github.gear4jtest.xml.parser.*`

## Source-level markers and guardrails

Gear4J also provides lightweight source markers in
`io.github.gear4jtest.core.api.annotation`:

- `@PublicApi` for application-facing contracts;
- `@Spi` for extension contracts implemented by application/integration code;
- `@Internal` for implementation details with no compatibility promise;
- `@Experimental` for contracts that may still change before the first stable
  release.

These annotations are documentation markers retained in class files. Public/SPI/internal markers are applied
at the main package boundaries so consumers can distinguish stable contracts from implementation packages directly in
the generated Javadocs and class files. `ApiBoundarySourceTest` requires exactly one marker on every production package
across all published Java library modules. Japicmp supplies the separate binary/source enforcement.

The repository intentionally does not introduce JPMS descriptors yet. Instead, source-level architecture tests enforce
that production packages declare a package marker and that exported API/SPI signatures in every published module have
zero dependencies on packages or individual types marked `@Internal`. Provider-neutral `external-api` is also
forbidden from importing JDBC packages. See `docs/decisions/0012-source-level-api-boundaries.md` for the rationale.

Some public packages contain individual classes or methods marked `@Internal`. Those members are implementation details
kept public for wiring, tests or historical compatibility; they are not part of the stable consumer contract.

## Generated XML definitions

XML with inline Java or operator class names remains a trusted-source feature.
The default XML translator is GEL-only and deny-all: Java snippets and
unregistered operator capabilities are rejected. Restricted callers configure
stable operator identifiers through `XmlOperatorCapabilityPolicy` and pass the
policy to `XmlOperationChainTranslator.gelOnly(policy)`. TEST and RUN mappings
are independent.

For externally authored definitions, use GEL conditions with:

```xml
<condition language="gel" expression="input.status == 'ACTIVE'"/>
```

GEL is intentionally limited: no Java method calls, no constructors, no class
literals, no reflection and no arbitrary static access.


## Stable facade rule

Application code should depend on `AssemblyLineExecutor`, `RunTrace`, `StationTrace`, `RunPersistenceManager` and
`PersistenceRuntimeMonitor`. `AssemblyLineEngine`, mutable trace implementations and execution registries remain internal
wiring types even when a public class is retained temporarily for binary or framework integration.
