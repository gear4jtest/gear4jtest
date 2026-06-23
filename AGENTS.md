# AGENTS.md

This file contains repository-wide instructions for coding agents working on Gear4J.

## Project overview

Gear4J is an experimental Java 17 pipeline and orchestration library.

The repository is a multi-module Gradle build. The core module owns the runtime engine and public Java API. Optional
modules add external pipeline loading, XML translation, Gradle XML generation, Jackson cloning and Spring integration.

## Repository layout

- `gear4jtest-core`: runtime engine, public Java API, stations, execution context, flow, events, persistence traces and
  extension SPI.
- `gear4jtest-external-api`: external pipeline artifacts, translators, in-memory compilation, classloading and
  dependency injection.
- `gear4jtest-xml`: XML validation, parsing and Java source generation.
- `gear4jtest-gradle-xml2java`: Gradle plugin for XML-to-Java generation.
- `gear4jtest-jackson`: optional Jackson-backed `PayloadCloner` implementation.
- `gear4jtest-jdbc`: optional JDBC execution persistence, dialects and schema migrations.
- `gear4jtest-spring`: lightweight Spring integration.

## Build and validation commands

Preferred validation sequence after code changes:

```bash
./gradlew spotlessApply
./gradlew check
```

Useful focused commands:

```bash
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-xml:test
./gradlew :gear4jtest-gradle-xml2java:test
```

Formatting-only validation:

```bash
./gradlew spotlessCheck
```

Static style validation is part of `./gradlew check` through Checkstyle.

The Gradle wrapper must be complete in the working copy: `gradlew`, `gradle/wrapper/gradle-wrapper.jar` and
`gradle/wrapper/gradle-wrapper.properties`.

## Formatting and style source of truth

The Gradle build is the only source of truth for Java formatting and style validation.

Java formatting is enforced by Spotless with the versioned Eclipse JDT profile in
`config/formatter/eclipse-java-formatter.xml`. Checkstyle adds lightweight hygiene rules from
`config/checkstyle/checkstyle.xml`.

Agents should not attempt to manually reproduce the Java style. After editing Java source files, run:

```bash
./gradlew spotlessApply
./gradlew check
```

`.editorconfig` defines editor-level basics such as charset, line endings, indentation, final newline and trailing
whitespace. It does not define the complete Java style.

Important Java wrapping convention: keep method and constructor parameters on the declaration line while the line fits;
when wrapping is required, keep the first parameter on the declaration line and align following parameters vertically.

Do not manually reformat large unrelated areas. If `spotlessApply` changes many unrelated files, keep that as a separate
formatting-only change or report it explicitly.

## Coding conventions

- Use Java 17.
- Prefer explicit, boring Java over clever abstractions.
- Keep public APIs intentional and document important API/SPI contracts.
- Do not add mechanical Javadocs to obvious getters or builders only to satisfy a tool; doclint missing-comment checks are intentionally disabled.
- Add or update tests for behavior changes.
- Prefer JUnit 5 and AssertJ.
- Use `// Given`, `// When`, `// Then` comments in non-trivial tests.

## Architectural invariants

- Keep `gear4jtest-core` framework-agnostic.
- Do not add Spring, XML, Jackson-specific, external transport or storage-specific dependencies to core.
- Runtime traces and persistence records are separate concepts.
- Station logs are observability data, not flow-control input.
- `EventManager` is in-memory and best-effort, not a durable broker.
- External event forwarding must not imply guaranteed delivery unless a separate durable subsystem is implemented.
- AssemblyLine references must preserve runtime graph stability during a run.
- Do not shut down executors supplied by callers unless ownership is explicit.
- Payload cloning belongs behind `PayloadCloner`.

## Generated code

Generated Java source should be readable and deterministic.

Generated files under `build/` are not repository source files and should not be edited directly. The generator should
produce readable source, but repository formatting validation applies to checked-in source files, not transient build
outputs.

For XML generator changes, prefer tests that cover the full path: validate, parse, generate, compile, instantiate,
inject and execute.

## Before finishing

- Run the most specific module test first when possible.
- Run `./gradlew spotlessApply` after source changes.
- Run `./gradlew check` before delivering a patch when possible.
- Do not bypass Checkstyle failures by weakening rules unless the user explicitly asks for a style policy change.
- Mention every command that could not be run and why.
