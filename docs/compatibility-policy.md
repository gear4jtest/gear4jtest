# Gear4J 1.x compatibility policy

## Scope

This policy starts with `1.0.0`. Packages marked `@PublicApi` or `@Spi` are compatibility-sensitive. Packages and types
marked `@Internal` or `@Experimental` are excluded unless a release note explicitly promotes them.

## Java source and binary compatibility

Patch and minor 1.x releases must not remove or narrow public/SPI classes, methods, constructors, fields or implemented
interfaces. The supported execution entry point is `AssemblyLineExecutor`; mutable engine and trace implementation classes are not part of the 1.x contract. Additive overloads and default SPI methods are preferred. A source-compatible change that is binary
incompatible is still a breaking change. A necessary incompatible change requires a major release, migration guide and
deprecation path whenever one can be provided safely.

`apiCompatibilityCheck` uses Japicmp against the previous stable artifact set:

```bash
./gradlew apiCompatibilityCheck -Pgear4j.apiBaselineVersion=1.2.3
```

The initial `1.0.0`, snapshots and prereleases have no earlier stable baseline. Every later stable release fails
`releaseCheck` when `gear4j.apiBaselineVersion` is missing. CI stores XML reports under `build/reports/japicmp`.

Generics added before the baseline (`RunRequest<IN>` and `GeneratedAssemblyLine<IN, OUT>`) preserve erased JVM method
descriptors while improving new source code. Raw use remains a Java migration aid but should not be used in new code.

## Gradle plugin DSL

The canonical plugin id is `io.github.gear4jtest.xml2java`. The legacy
`io.github.gear4jtest.gradle.xml2java` id remains supported throughout 1.x and
resolves to the same implementation.

The following build-script surface is compatibility-sensitive:

- the `xmlAssemblyLineGenerator` extension and its
  `XmlAssemblyLineGeneratorExtension` type;
- `xmlFiles`, `inputDir`, `filePaths`, `outputDir`, `mediaType`, `trustedXml`,
  `operatorCapabilities`, `operatorCapability`, `maxOperations`,
  `maxDependencies`, `maxNestingDepth`, `maxXmlBytes` and
  `maxGeneratedSourceBytes`;
- the `xmlGenerateAssemblyLine` task name and the names and Gradle property
  types of its annotated inputs and output.

`XmlAssemblyLineGeneratorPlugin` and `XmlAssemblyLineGenerateTask` are public
only because Gradle instantiates and decorates them. They are marked
`@Internal`; consumers must configure the extension or the named task rather
than construct, subclass or depend on those implementation classes.

Japicmp compares the published `gear4jtest-gradle-xml2java` implementation
artifact with the configured N-1 release. The separate
`gradlePluginCompatibilityTest` runs the versioned 1.0 consumer fixture against
the current plugin through both plugin ids. Future stable releases update or
add an immediately preceding fixture instead of rewriting the historical 1.0
contract.

The tested Gradle runtime for the 1.0 line is the checksummed wrapper version,
Gradle 9.6.1 on Java 17. Compatibility with other Gradle versions is not claimed
until that version is added to the TestKit/CI matrix. Changing the minimum or
tested Gradle runtime is a documented build-tool compatibility change.

## SPI compatibility

SPI changes receive the same binary/source checks as public API. New abstract methods are forbidden in a minor or patch
release unless all implementations are owned by the same artifact and cannot be supplied by consumers. Prefer default
methods with a behaviorally safe default and document concurrency, ownership, timeout and failure semantics.

## XML and media types

The canonical XML media type is `application/vnd.gear4j.assembly-line+xml`. `application/xml`, `text/xml` and compatible
`+xml` types remain accepted during 1.x. XSD changes in a patch/minor release must continue to validate documents accepted
by the previous minor version unless a security fix requires rejection; such rejection must be called out prominently.
New optional elements/attributes are additive. Renames, required fields and semantic reinterpretation require a major
version or an explicit versioned schema/media type.

For restricted XML, `processingOperation/@type` is a stable operator capability
id resolved through `XmlOperatorCapabilityPolicy`; trusted XML retains Java class
name semantics. Removing or renaming a published capability id is a behavioral
compatibility change. TEST and RUN mappings may differ, but narrowing RUN access
must be called out as a security-relevant migration.

## Database schema and migrations

Committed migrations are immutable: their content and checksums are never rewritten after release. Patch/minor releases
may add forward migrations that preserve existing data and can be applied from the previous supported minor version.
Gear4J supports upgrade from the immediately preceding stable minor (N-1). Destructive migrations require operator
documentation, backup/restore guidance and a major release. Dialect resources must remain behaviorally aligned across
PostgreSQL, MySQL, MariaDB, Oracle and the H2 test dialect.

## Configuration properties

Existing `gear4j.*` property names, accepted value formats and safe defaults remain compatible in patch/minor releases.
New properties require a safe default. Renames use a deprecation period with startup diagnostics; ambiguous simultaneous
old/new values fail fast. A security hardening may replace an unsafe default, but the release notes must include the
operational migration.

## Metrics and health contracts

Metric names, units, bounded tag names/values and health-indicator meanings are public operational contracts. Patch/minor
releases may add metrics but do not remove or silently change existing series. High-cardinality identifiers never become
default tags. Renames require an overlap period or a major release. Process-wide event metrics intentionally remain
tag-free.

## Deprecation and support window

Deprecations identify the replacement and earliest removal major. The supported migration target is the immediately
preceding stable minor release. Release notes must separately list Java API, XML/XSD, DB, property and metric changes so
operators can assess upgrades without inspecting implementation commits.
