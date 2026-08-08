# Gear4J

Gear4J is an experimental Java 17 pipeline and orchestration library.

It lets applications describe a pipeline as an `AssemblyLine`, compose it from typed stations, execute it through a
runtime engine, and extend that runtime with persistence, events, side-compute, resource lookup, payload cloning and
framework integrations.

The project is still evolving quickly. The public API and module boundaries are not yet considered stable.

## Goals

Gear4J aims to provide:

- a small Java API for sequential, conditional, iterative and parallel pipeline execution;
- explicit runtime behavior for failures, stop/cancel signals, event dispatching and persistence;
- extension points that keep the core framework-agnostic;
- optional modules for XML-defined pipelines, Jackson-based cloning and Spring integration;
- a future path toward externally managed pipelines authored by tools or back offices.

## Module map

| Module                       | Purpose                                                                                                                                                        |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `gear4jtest-core`            | Runtime engine, public Java API, stations, flow policies, events, side-compute, persistence traces and extension SPI.                                          |
| `gear4jtest-experimental-cache` | Experimental assembly-line cache helpers intended for opt-in personal/advanced usage; API may disappear before 1.0.                              |
| `gear4jtest-external-api`    | Provider-neutral contracts and infrastructure for externally stored pipeline definitions: artifacts, repositories, translators, compilation and loading. |
| `gear4jtest-external-jdbc`   | Optional JDBC repositories, schema migrations and database-backed artifact storage for external definitions.                                         |
| `gear4jtest-xml`             | XML parser, validator and Java source generator for externalized Gear4J pipelines.                                                                             |
| `gear4jtest-gradle-xml2java` | Gradle plugin that generates Java pipeline classes from XML files at build time.                                                                               |
| `gear4jtest-jackson`         | Optional Jackson-backed `PayloadCloner` implementation.                                                                                                        |
| `gear4jtest-jdbc`            | Optional JDBC execution persistence, database dialects and schema migrations.                                                                                   |
| `gear4jtest-spring`          | Lightweight Spring integration for resource lookup, engine creation and extension discovery.                                                                   |
| `gear4jtest-micrometer`      | Optional bounded-cardinality metrics for lifecycle, generated loading, classloaders, artifacts, events and JDBC persistence.                                   |
| `gear4jtest-spring-boot-starter` | Spring Boot auto-configuration with validated `gear4j.*` properties, optional JDBC persistence and Micrometer wiring.                                    |

## Core concepts

- **AssemblyLine**: a pipeline definition with an id, version, root station, default context and runtime configuration.
- **Station**: an executable pipeline element. The core supports work stations, sequences, containers, if/else
  containers, iterators, signal stations and pipeline calls.
- **Operator / Processor**: user code invoked by stations.
- **RunRequest&lt;IN&gt;**: typed per-run input, context, resource factory override, id generator override and runtime extensions.
- **ExecutionContext**: mutable state for a single run.
- **ExecutionServices**: run-scoped capabilities such as event publication, resource resolution and station-scoped
  resources.
- **RuntimeExtension**: extension SPI used to wrap runs, stations, executors or observe lifecycle events.
- **EventPublisher**: stable run-scoped publication capability backed by an internal, best-effort in-memory runtime.
- **PayloadCloner**: SPI used to isolate branch inputs when mutable payloads are executed in containers.

## Important non-guarantees

Gear4J is a runtime library, so the most important operational limits are explicit:

- the in-memory event runtime is best-effort and does not provide durable delivery, replay or exactly-once semantics;
- cancellation and timeouts are cooperative for user Java code and cannot forcibly stop arbitrary blocking operators;
- XML trusted mode is equivalent to compiling reviewed Java source in the application JVM, not a sandbox;
- restricted XML is GEL-only and may invoke only operator capabilities
  explicitly allowlisted for TEST or RUN;
- XML translation and generated compilation have finite operation, dependency,
  nesting, source, bytecode and cumulative classloader-weight limits;
- direct persistence managers and the Spring Boot starter discard input/context/result/error payloads by default;
  deployments that capture selected values should provide a `SensitiveDataRedactor`. Raw capture requires an explicit
  `SensitiveDataRedactor.none()`, Spring Boot `redaction-mode=DISABLED`, or the deprecated explicit `WARN` mode;
- generated classloaders are cached locally per JVM; alias invalidation is local, not a distributed cache protocol.

## Build and test

The repository is a multi-module Gradle project. Dependency coordinates are centralized in the Gradle version catalog at
`gradle/libs.versions.toml`; module build files should use typed catalog accessors such as `libs.junit` or `libs.spring.boot.autoconfigure` rather than hard-coded dependency versions
versions.

Common commands:

```bash
./gradlew clean build
./gradlew coverageReport
./gradlew coverageVerification
./gradlew verifyPerformanceBudgets
./gradlew sonarqube
./gradlew dependencyCheckAggregate
./gradlew stageMavenCentral -PprojectVersion=1.0.0
scripts/verify-reproducible-staging.sh 1.0.0
./gradlew consumerSmokeTest -PprojectVersion=1.0.0
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-xml:test
```

`./gradlew clean build` is the normal project verification command: it compiles the modules, runs unit tests, runs
integration tests through `integrationCheck`, executes style checks and enforces the critical-class branch-coverage
ratchets, including versioned module-level floors. Aggregate JaCoCo report generation is explicit through
`./gradlew coverageReport`; a targeted subproject
build no longer triggers every test and report in the repository. `./gradlew sonarqube` depends on that aggregate
report but is not wired into `build`.

`verifyPerformanceBudgets` runs the versioned JMH scenarios for event filtering, GEL, payload cloning, generated-source
compilation, the experimental cache, JDBC batching and 8 MiB artifact streaming. It is intentionally slower than the
normal PR build and runs on the main branch, on the weekly schedule and as part of `releaseCheck`. See
`docs/performance.md`.

Unit tests live under `src/test`. Integration tests live under `src/integrationTest` and are executed by the
`integrationTest` tasks, which are part of the default `check`/`build` lifecycle. Tests that need databases use
Testcontainers, so the container lifecycle is declared in the JUnit tests themselves. The default JDBC selection is
`all`: an unqualified `build`, `check` or JDBC `integrationTest` validates PostgreSQL, MySQL, MariaDB and Oracle. Use
`-Pgear4jDatabaseDialect=<dialect>` only for an explicit local fast path or for an individual CI matrix job. Maven
Central staging writes artifacts under `build/staging-deploy`; deployment is handled by JReleaser from the
`release.yml` GitHub Actions workflow.

`releaseCheck` also builds the standalone project under `config/consumer-smoke` against the staged repository. This
guards the published POM scopes and the Gradle plugin marker instead of relying only on intra-repository project
dependencies. See `docs/releasing.md` for the release contract and required credentials.


For the 1.0 line, advanced Gradle dependency locking and checksum-verification metadata are intentionally deferred.
The retained baseline is the checksummed Gradle wrapper, SHA-pinned GitHub Actions, Maven Central-only repositories,
OWASP vulnerability scanning and reproducible staged artifacts. Staged JAR, POM and Gradle module metadata outputs
are rebuilt and hash-compared by `scripts/verify-reproducible-staging.sh`.

The Gradle wrapper must be complete in the working copy: `gradlew`, `gradle/wrapper/gradle-wrapper.jar` and
`gradle/wrapper/gradle-wrapper.properties`.

## Code style and validation

Gear4J uses Spotless with a versioned Eclipse JDT formatter profile for checked-in Java source files, plus a lightweight
Checkstyle ruleset for project hygiene.

Useful commands:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew check
```

`spotlessCheck`, Checkstyle and integration tests are wired into `check`, so `./gradlew check` and `./gradlew build`
fail when checked-in sources do not follow the repository rules or when unit/integration tests fail.

The Gradle build is the source of truth for Java formatting and validation. `.editorconfig` only defines editor-level
basics such as UTF-8, LF line endings, indentation, final newline and trailing whitespace.

Java formatter, validation and IDE convenience files live under:

- `config/formatter/eclipse-java-formatter.xml`
- `config/checkstyle/checkstyle.xml`
- `config/checkstyle/checkstyle-suppressions.xml`
- `config/ide/`
- `.idea/codeStyles/`
- `.vscode/settings.json`
- `docs/contributing/code-style.md`

Generated files under `build/` are excluded from repository style validation. Generators should still produce readable
deterministic output, but transient generated files should not be edited directly.

## Documentation map

- `gear4jtest-core/README.md`: runtime concepts and extension model.
- `gear4jtest-external-api/README.md`: external pipeline loading and compilation.
- `gear4jtest-external-jdbc/README.md`: optional external-definition JDBC storage and migrations.
- `gear4jtest-xml/README.md`: XML translation model.
- `gear4jtest-gradle-xml2java/README.md`: Gradle XML generation plugin.
- `docs/architecture/`: durable architecture notes.
- `docs/audit/closure-matrix-2026-07-13.md`: status and residual backlog for all 47 audit findings.
- `docs/contributing/code-style.md`: style, formatter and Checkstyle rules.
- `docs/decisions/`: decision records and future-direction notes, including source-level API boundary policy.
- `docs/roadmap/future-work.md`: known work items and non-MVP ideas.
- `docs/releasing.md`: Maven Central release process.
- `docs/security/dependency-supply-chain.md`: current 1.0 dependency-security baseline and deferred hardening backlog.
- `AGENTS.md`: instructions for coding agents such as Codex, Claude, Gemini or IDE assistants.
- `CLAUDE.md`: Claude-specific entrypoint that delegates to `AGENTS.md`.
- `.github/copilot-instructions.md`: GitHub Copilot guidance.

## License

Gear4J is distributed under the [Apache License, Version 2.0](LICENSE). Project attribution is recorded in
[NOTICE](NOTICE). Published JARs include both files under `META-INF`.

## Status

The project is in active design and implementation. Prefer small, well-tested changes. When in doubt, keep the core
simple and move optional integrations to dedicated modules.

See `docs/production-readiness.md` before using Gear4J operationally.
