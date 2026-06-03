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
| `gear4jtest-external-api`    | Contracts and infrastructure for externally stored pipeline definitions: artifacts, translators, in-memory compilation, classloaders and dependency injection. |
| `gear4jtest-xml`             | XML parser, validator and Java source generator for externalized Gear4J pipelines.                                                                             |
| `gear4jtest-gradle-xml2java` | Gradle plugin that generates Java pipeline classes from XML files at build time.                                                                               |
| `gear4jtest-jackson`         | Optional Jackson-backed `PayloadCloner` implementation.                                                                                                        |
| `gear4jtest-spring`          | Lightweight Spring integration for resource lookup, engine creation and extension discovery.                                                                   |
| `gear4jtest-micrometer`      | Optional Micrometer lifecycle metrics for runs, stations and JDBC persistence.                                                                                 |
| `gear4jtest-spring-boot-starter` | Spring Boot auto-configuration with validated `gear4j.*` properties, optional JDBC persistence and Micrometer wiring.                                    |

## Core concepts

- **AssemblyLine**: a pipeline definition with an id, version, root station, default context and runtime configuration.
- **Station**: an executable pipeline element. The core supports work stations, sequences, containers, if/else
  containers, iterators, signal stations and pipeline calls.
- **Operator / Processor**: user code invoked by stations.
- **RunRequest**: per-run input, context, resource factory override, id generator override and runtime extensions.
- **ExecutionContext**: mutable state for a single run.
- **ExecutionServices**: run-scoped services such as event manager, resource factory and station-scoped resources.
- **RuntimeExtension**: extension SPI used to wrap runs, stations, executors or observe lifecycle events.
- **EventManager**: in-memory asynchronous event runtime. It is deliberately best-effort, not a durable broker.
- **PayloadCloner**: SPI used to isolate branch inputs when mutable payloads are executed in containers.

## Build and test

The repository is a multi-module Gradle project.

Common commands:

```bash
./gradlew clean test
./gradlew check
./gradlew integrationCheck
./gradlew dependencyCheckAggregate
./gradlew stageMavenCentral -PprojectVersion=1.0.0
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-xml:test
```

Unit tests live under `src/test`. Docker-backed tests live under `src/integrationTest` and are executed by the
`integrationTest` tasks. Maven Central staging writes artifacts under `build/staging-deploy`; deployment is handled by
JReleaser from the `release.yml` GitHub Actions workflow.

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

`spotlessCheck` and Checkstyle are wired into `check`, so `./gradlew check` and `./gradlew build` fail when checked-in
sources do not follow the repository rules.

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
- `gear4jtest-xml/README.md`: XML translation model.
- `gear4jtest-gradle-xml2java/README.md`: Gradle XML generation plugin.
- `docs/architecture/`: durable architecture notes.
- `docs/contributing/code-style.md`: style, formatter and Checkstyle rules.
- `docs/decisions/`: decision records and future-direction notes.
- `docs/roadmap/future-work.md`: known work items and non-MVP ideas.
- `docs/releasing.md`: Maven Central release process.
- `docs/security/dependency-supply-chain.md`: dependency locking, verification and SCA guidance.
- `AGENTS.md`: instructions for coding agents such as Codex, Claude, Gemini or IDE assistants.
- `CLAUDE.md`: Claude-specific entrypoint that delegates to `AGENTS.md`.
- `.github/copilot-instructions.md`: GitHub Copilot guidance.

## Status

The project is in active design and implementation. Prefer small, well-tested changes. When in doubt, keep the core
simple and move optional integrations to dedicated modules.
