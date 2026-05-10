# AGENTS.md

This file is for coding agents working on Gear4J.

Read it before editing code. Also read a module-level `AGENTS.md` when one exists under the directory you are modifying.

## Project overview

Gear4J is a Java 17 pipeline/orchestration library.

The repository currently uses the `gear4jtest-*` module names while the project is still evolving.

## Repository layout

- `gear4jtest-core`: runtime engine and public Java API.
- `gear4jtest-external-api`: common external pipeline loading, artifact, compiler, classloader and dependency-injection infrastructure.
- `gear4jtest-xml`: XML translator and Java source generator for externalized pipelines.
- `gear4jtest-gradle-xml2java`: Gradle plugin for XML-to-Java generation.
- `gear4jtest-jackson`: optional Jackson `PayloadCloner` implementation.
- `gear4jtest-spring`: lightweight Spring integration, not Spring Boot auto-configuration.
- `docs`: human and agent-readable architecture notes.

## Build and validation commands

Preferred commands:

```bash
./gradlew clean test
./gradlew check
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-xml:test
```

The Gradle wrapper must be complete in the working copy:

- `gradlew`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

If `./gradlew` fails because `GradleWrapperMain` cannot be found, restore the wrapper files before changing runtime code.

## Coding conventions

- Use Java 17.
- Prefer explicit, boring Java over clever abstractions.
- Keep public APIs understandable from the call site.
- Avoid large unrelated refactors.
- Add or update tests for behavior changes.
- Prefer JUnit 5 and AssertJ.
- Use `// Given`, `// When`, `// Then` in non-trivial tests.
- Mention every validation command you ran.
- Mention commands you could not run and why.

## Architectural invariants

- Keep `gear4jtest-core` framework-agnostic.
- Do not introduce Spring, XML, Jackson-specific behavior, transport or storage dependencies into core.
- Runtime traces and persistence records are separate concepts.
- Station logs and run traces are observability data, not flow-control inputs.
- `EventManager` is in-memory, asynchronous and best-effort. It is not a durable broker.
- Durable event delivery must be a separate subsystem or module.
- Payload isolation belongs behind `PayloadCloner`.
- Do not shut down executors supplied by callers unless the ownership contract explicitly says Gear4J owns them.
- A running pipeline graph must stay stable for the duration of a run.
- Pipeline references should preserve declared and resolved forms where aliasing is involved.
- STOP and CANCEL are flow outcomes, not generic exceptions used for normal control flow.
- JVM `Error` should not be swallowed as an ordinary recoverable pipeline failure.

## Module boundaries

### Core

`gear4jtest-core` owns runtime execution and public Java API. It must not depend on XML, Spring, Jackson-specific cloning, Kafka, SQS, JDBC schema details for external artifacts, or Gradle plugin behavior.

### External API

`gear4jtest-external-api` owns common infrastructure for externally stored definitions. It must not contain XML-specific parsing or generation logic.

### XML

`gear4jtest-xml` owns XML validation, parsing and Java generation. Generated classes should implement `GeneratedAssemblyLine`, remain no-arg constructible and use `@Inject` fields for external dependencies.

### Gradle plugin

`gear4jtest-gradle-xml2java` wires XML generation into Gradle. Do not duplicate generator logic here.

### Jackson

`gear4jtest-jackson` owns Jackson-based cloning only. Do not move it into core.

### Spring

`gear4jtest-spring` owns plain Spring integration. Boot auto-configuration belongs in a future dedicated module.

## Documentation rules

- Update README files when public usage or module responsibility changes.
- Update `docs/architecture` when an architectural invariant changes.
- Add a decision record under `docs/decisions` for durable trade-offs or future-direction decisions.
- Clearly mark future ideas as `Status: Future direction` or `Status: Not implemented`.
- Do not document planned features as if they already exist.

## Before finishing a change

1. Run the most focused test task for the modified module.
2. Run broader validation when practical.
3. Include a concise summary of changed files and behavior.
4. Call out any unvalidated area.
