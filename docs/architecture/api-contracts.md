# API, SPI and internal contracts

Gear4J is a library, so package boundaries must be explicit before the first
production release. This document defines the current compatibility contract.

## Public API

Public API is intended for application developers that execute or define
pipelines directly.

Current public API packages include:

- `io.github.gear4jtest.core.api.*`
- `io.github.gear4jtest.core.event` event contracts used by applications that subscribe to runtime events
- `io.github.gear4jtest.core.exception` application-visible exception hierarchy
- `io.github.gear4jtest.core.model` application-visible runtime status values
- `io.github.gear4jtest.core.persistence` records and repositories used by
  applications that enable JDBC persistence
- `io.github.gear4jtest.external.api.AssemblyLineManager`
- `io.github.gear4jtest.external.api.ExecutionMode`
- `io.github.gear4jtest.external.api.StoreType`
- `io.github.gear4jtest.xml.translator.XmlOperationChainTranslator`

Compatibility expectation after the first stable release:

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
- implementations must not retain mutable caller-owned data without copying;
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
- `io.github.gear4jtest.core.event.*` except documented event contracts
- `io.github.gear4jtest.core.persistence.migration.*`
- `io.github.gear4jtest.xml.generator.*`
- `io.github.gear4jtest.xml.parser.*`

## Source-level markers

Gear4J also provides lightweight source markers in
`io.github.gear4jtest.core.api.annotation`:

- `@PublicApi` for application-facing contracts;
- `@Spi` for extension contracts implemented by application/integration code;
- `@Internal` for implementation details with no compatibility promise;
- `@Experimental` for contracts that may still change before the first stable
  release.

These annotations are documentation markers retained in class files. Public/SPI/internal markers are now also applied
at the main package boundaries so consumers can distinguish stable contracts from implementation packages directly in
the generated Javadocs and class files. They do not enforce binary compatibility by themselves; the package contract
above remains the source of truth.

## Generated XML definitions

XML with inline Java remains a trusted-source feature. The default XML translator
is GEL-only/untrusted: Java snippets are rejected unless callers explicitly use
`XmlOperationChainTranslator.trusted()` or `XmlToJavaGenerator.trusted()`.

For externally authored definitions, use GEL conditions with:

```xml
<condition language="gel" expression="input.status == 'ACTIVE'"/>
```

GEL is intentionally limited: no Java method calls, no constructors, no class
literals, no reflection and no arbitrary static access.
