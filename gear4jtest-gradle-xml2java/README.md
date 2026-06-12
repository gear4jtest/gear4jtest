# gear4jtest-gradle-xml2java

`gear4jtest-gradle-xml2java` is a Gradle plugin for generating Java Gear4J pipeline classes from XML files during a
build.

It is separate from the runtime external-loading path. The plugin is useful when XML definitions should become generated
Java source at build time instead of being compiled dynamically at application runtime.

## Plugin id

```text
io.github.gear4jtest.xml2java
```

A temporary legacy alias is also registered for compatibility with early local experiments:

```text
io.github.gear4jtest.gradle.xml2java
```

## Default behavior

When the plugin is applied to a Java project, it:

- creates the `xmlAssemblyLineGenerator` extension;
- creates the `xmlGenerateAssemblyLine` task;
- reads `**/*.xml` under `src/main/gear4j` by default;
- writes generated Java sources to `build/generated/sources/gear4j/xml2java/main`;
- adds that directory to the main Java source set;
- makes `compileJava` depend on `xmlGenerateAssemblyLine`.

## Example usage

```groovy
plugins {
    id 'java'
    id 'io.github.gear4jtest.xml2java'
}

xmlAssemblyLineGenerator {
    inputDir 'src/main/gear4j'
    outputDir = layout.buildDirectory.dir('generated/sources/gear4j/xml2java/main').get().asFile
}
```

You can also add explicit files or collections:

```groovy
xmlAssemblyLineGenerator {
    xmlFiles file('pipelines/checkout.xml')
    xmlFiles fileTree('more-pipelines') { include '**/*.xml' }
}
```

Older local builds that used `filePaths = '...'` are still supported as an alias for `inputDir`.

## Responsibilities

This module owns:

- Gradle plugin registration;
- plugin extension properties;
- wiring XML inputs to generated Java outputs;
- integration with `gear4jtest-xml` generation code;
- Gradle plugin tests.

It should not duplicate XML parsing or generation logic. That belongs in `gear4jtest-xml`.

## Testing

Useful focused task:

```bash
./gradlew :gear4jtest-gradle-xml2java:test
```

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
