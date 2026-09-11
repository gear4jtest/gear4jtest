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
- resolves operator signatures from a declared classpath;
- creates an independent `gear4jOperators` source set for same-project operators;
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
    maxOperations.set(500)
    maxDependencies.set(64)
    maxNestingDepth.set(16)
    maxXmlBytes.set(2L * 1024L * 1024L)
    maxGeneratedSourceBytes.set(2L * 1024L * 1024L)
}
```

If not configured, the task uses the runtime XML defaults: 1,000 total
operations, 256 dependencies, nesting depth 32, 2 MiB per XML input and 4 MiB
of generated UTF-8 source. All five values are declared task inputs, so changing a budget
invalidates the build-cache entry.

Each XML file is read through the configured `maxXmlBytes` budget. The task reads
at most one byte beyond that limit before rejecting an oversized input, and it
does so before replacing previously generated sources.

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

## Operator classpath and same-project operators

Generation reflects on compiled operator classes to resolve `Operator<IN, OUT>`.
Registering a capability does not make its Java class available by itself.
With the Java plugin, `operatorClasspath` includes the main compile classpath
and the output/runtime dependencies of `gear4jOperators`. An operator from an
`implementation` project or JAR dependency is therefore available automatically.
The application must still declare its Gear4J runtime dependencies normally.

For Java operators defined in the same project, place their sources under
`src/gear4jOperators/java`. Package names do not change. The build order is:

1. compile `gear4jOperators`;
2. generate Java from XML;
3. compile main Java, including generated sources.

These operator classes are added to main/test classpaths and to the application
JAR. When `withSourcesJar()` is enabled, operator sources are included as well.
Operator dependencies inherit `implementation`, `compileOnly` and `runtimeOnly`;
additional operator-specific dependencies can use `gear4jOperatorsImplementation`.

Operators and their supporting types must be compilable without main output or
generated pipelines. Put shared types in this source set or a separate module.
Do not add `sourceSets.main.output` or `sourceSets.main.runtimeClasspath` to the
generation classpath: main compilation depends on generation, creating a cycle.
The plugin does not precompile arbitrary `src/main/java` files.

Additional already compiled classes can be provided explicitly:

```groovy
xmlAssemblyLineGenerator {
    operatorClasspath.from(files('lib/reviewed-operators.jar'))
}
```

An explicit generation-only classpath does not add application dependencies;
also declare the JAR in `implementation` if generated code references it.
For task-produced outputs, supply a task-backed file collection so Gradle can
infer the producer dependency.

The classpath is an `@Classpath` input: class bytecode changes invalidate the
task even when XML and public signatures are unchanged. Each execution closes
its operator classloader and restores the thread context classloader, including
failure paths. The capability policy still applies in restricted XML mode.

## Compatibility contract

For Gear4J 1.x, both plugin ids, the `xmlAssemblyLineGenerator` extension name,
its documented properties and methods, and the `xmlGenerateAssemblyLine` task
inputs/output are stable build-script contracts. The plugin implementation and
task classes themselves are internal Gradle wiring and must not be constructed
or subclassed by consumers.

The versioned `compatibility/1.0` TestKit fixture exercises the complete DSL
through both the canonical and legacy plugin ids. Japicmp also compares the
published plugin implementation artifact whenever
`gear4j.apiBaselineVersion` is configured.

The tested Gradle runtime for Gear4J 1.0 is Gradle 9.6.1 on Java 17. Other
Gradle versions are not part of the supported matrix until they are exercised
by TestKit and CI.

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
./gradlew :gear4jtest-gradle-xml2java:gradlePluginCompatibilityTest
./gradlew :gear4jtest-gradle-xml2java:javadocJar
```

The TestKit suite executes the real generation task twice with strict
configuration-cache validation and the build cache enabled. It also verifies that
changing an XML input invalidates the task and replaces obsolete generated files.
The published Javadoc-classified artifact is generated with Groovydoc because the
stable Gradle DSL type is implemented in Groovy; the task fails if that public type
is absent from the archive. The module applies the Groovy plugin directly so the
documentation task is available independently of root-project convention wiring.
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
