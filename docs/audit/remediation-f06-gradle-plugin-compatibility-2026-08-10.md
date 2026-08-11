# Remediation F-06 - Gradle plugin compatibility

Date: 2026-08-10

## Scope

This remediation closes the audit gap that left the published
`gear4jtest-gradle-xml2java` DSL outside the API compatibility gates.

## Implemented guarantees

- The plugin implementation artifact participates in the N-1 Japicmp task set.
- `XmlAssemblyLineGeneratorExtension` is the stable public DSL type.
- The published `-javadoc.jar` is generated from that Groovy DSL type and its
  expected class documentation is verified before publication.
- The plugin and task implementation classes are explicitly internal.
- The package-marker architecture test covers both Java and Groovy production
  source roots.
- A versioned 1.0 TestKit fixture applies the current plugin through the
  canonical and legacy ids.
- The fixture exercises the extension methods/properties and every annotated
  generation-task input/output property, then proves configuration-cache reuse.
- `check`, `apiCompatibilityCheck`, `releaseCheck` and CI all execute the
  compatibility fixture.

## Compatibility decision

Both plugin ids and the documented extension/task DSL remain stable throughout
Gear4J 1.x. Consumers must not construct or subclass the internal plugin and
task implementation types. The tested build runtime for 1.0 is Gradle 9.6.1 on
Java 17; additional Gradle versions require an explicit TestKit/CI matrix entry.

## Javadoc publication hotfix

The first connected phase-7 build exposed that `withJavadocJar()` delegated to
the Java `javadoc` task even though the module's only stable type is implemented
in Groovy. The Java source set contains only `package-info.java`, so Javadoc
failed with `No public or protected classes found to document`.

The module now generates the Javadoc-classified artifact from a focused
Groovydoc task and verifies that the stable extension page is present. The Java
Javadoc task is skipped only while its source set contains package/module
descriptors and automatically becomes active when a Java type is added. The
module applies the Groovy plugin directly before resolving that task, rather
than relying on root-project plugin application order.

## Compatibility fixture resource hotfix

The connected build also exposed duplicate TestKit fixture resources. Creating
the `compatibilityTest` source set already registers the conventional
`src/compatibilityTest/groovy` and `src/compatibilityTest/resources` directories.
The explicit `srcDir` declarations registered both directories a second time,
so `processCompatibilityTestResources` encountered each fixture twice. The
redundant declarations were removed instead of suppressing duplicates, keeping
real resource collisions visible to the build.

## Qualification

The source tree passes Java 17 syntax parsing, package-marker enumeration, XML
fixture parsing and workflow YAML parsing in the constrained audit environment.
The Gradle wrapper cannot start there because the Gradle 9.6.1 distribution is
not available locally and access to `services.gradle.org` is blocked.

Run the connected qualification with:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-gradle-xml2java:javadocJar
./gradlew :gear4jtest-gradle-xml2java:gradlePluginCompatibilityTest
./gradlew :gear4jtest-core:test --tests '*ApiBoundarySourceTest'
./gradlew apiCompatibilityCheck
./gradlew check
```

For a stable release after 1.0.0, add the immediately preceding version:

```bash
./gradlew apiCompatibilityCheck \
  -PprojectVersion=1.0.1 \
  -Pgear4j.apiBaselineVersion=1.0.0
```
