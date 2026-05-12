# gear4jtest-gradle-xml2java

`gear4jtest-gradle-xml2java` is a Gradle plugin for generating Java Gear4J pipeline classes from XML files during a
build.

It is separate from the runtime external-loading path. The plugin is useful when XML definitions should become generated
Java source at build time instead of being compiled dynamically at application runtime.

## Plugin id

```text
io.test.gear4jtest.xml2java
```

## Responsibilities

This module owns:

- Gradle plugin registration;
- plugin extension properties;
- wiring XML inputs to generated Java outputs;
- integration with `gear4jtest-xml` generation code;
- Gradle TestKit coverage.

It should not duplicate XML parsing or generation logic. That belongs in `gear4jtest-xml`.

## Expected usage shape

A consumer build should apply the plugin, configure XML input locations and generated source output, then compile the
generated Java together with the rest of the project.

Exact extension names and defaults should be documented here whenever the plugin API stabilizes.

## Testing

Useful focused task:

```bash
./gradlew :gear4jtest-gradle-xml2java:test
```

Prefer Gradle TestKit tests for behavior that affects real consumer builds.

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
