# GitHub Copilot instructions for Gear4J

This is a Java 17 multi-module Gradle project.

Read `AGENTS.md` before suggesting code changes. It contains the repository-wide source of truth for agent behavior.

Key rules:

- Keep `gear4jtest-core` framework-agnostic.
- Do not introduce Spring, XML, Jackson-specific, transport-specific or persistence-specific dependencies into core.
- Preserve the separation between runtime traces and persistence records.
- Treat `EventManager` as an in-memory best-effort runtime, not as a durable broker.
- Add or update JUnit 5 + AssertJ tests for behavior changes.
- Do not rely on IDE or personal formatting preferences.
- Java formatting is controlled by Spotless with `config/formatter/eclipse-java-formatter.xml`; style hygiene is
  controlled by Checkstyle.
- After editing Java or build files, run `./gradlew spotlessApply` and `./gradlew check` when possible.
