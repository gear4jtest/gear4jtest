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
- translates all XML inputs before replacing the output directory, so a failed translation does not wipe previously
  generated sources;
- rejects inline Java and unregistered operator capabilities by default unless
  `trustedXml` is enabled explicitly;
- adds that directory to the main Java source set;
- makes `compileJava` depend on `xmlGenerateAssemblyLine`;
- is cacheable and compatible with the Gradle configuration cache;
- restores generated outputs from the build cache when inputs are unchanged.

## Example usage

```groovy
plugins {
    id 'java'
    id 'io.github.gear4jtest.xml2java'
}

xmlAssemblyLineGenerator {
    inputDir 'src/main/gear4j'
    outputDir.set(layout.buildDirectory.dir('generated/sources/gear4j/xml2java/main'))
}
```

XML definitions are treated as untrusted by default. This allows XML using GEL-only expressions, but rejects inline Java
expressions such as method references, Java lambdas or fallback snippets.
Restricted processing operations use stable capability ids:

```xml
<processingOperation id="normalize" type="customer.normalize"/>
```

Register the corresponding build-time operator classes in trusted Gradle
configuration:

```groovy
xmlAssemblyLineGenerator {
    operatorCapability 'customer.normalize', 'com.example.NormalizeCustomer'
    operatorCapability 'address.validate', 'com.example.ValidateAddress'
}
```

The capability mapping is an input of the cacheable generation task. If the XML
files are reviewed and versioned as source code, opt in explicitly:

```groovy
xmlAssemblyLineGenerator {
    inputDir 'src/main/gear4j'
    trustedXml()
}
```

Treat `trustedXml()` exactly like adding Java source files to the build. Do not enable it for XML received from users,
unreviewed PRs or external systems.

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

The TestKit suite executes the real generation task twice with strict
configuration-cache validation and the build cache enabled. It also verifies that
changing an XML input invalidates the task and replaces obsolete generated files.
A direct consumer-style check is:

```bash
./gradlew xmlGenerateAssemblyLine \
  --configuration-cache \
  --configuration-cache-problems=fail \
  --build-cache
```

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
