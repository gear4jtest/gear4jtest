# Phase 11 hotfix — TestKit cache fixture independence

**Date:** 22 July 2026
**Baseline:** phase 11 compiler and Gradle cache hardening

## Failure

`XmlAssemblyLineGeneratorFunctionalTest` referenced
`com.myorg.operation.Step11` from the temporary TestKit build. The class exists
in the plugin module's test source set, but `withPluginClasspath()` only exposes
the plugin-under-test runtime classpath to the nested build; it does not expose
arbitrary test classes as consumer operator dependencies.

The nested `xmlGenerateAssemblyLine` task therefore failed before exercising
configuration-cache or build-cache behavior:

```text
Unable to load operator class 'com.myorg.operation.Step11' to resolve its generic signature
```

## Correction

The functional cache fixture now uses a valid dependency-free signal pipeline:

```xml
<operations>
  <signal id="stop" type="STOP" inputType="java.lang.String"/>
</operations>
```

The temporary build no longer enables `trustedXml()`, because the fixture does
not contain inline Java. This keeps the functional test focused on the behavior
it is intended to prove:

- real task execution;
- strict configuration-cache reuse;
- build-cache restoration;
- task invalidation and stale-output replacement after XML changes.

`Step11` remains in the plugin test source set because the focused plugin unit
test uses it to exercise operator-signature generation in the in-process test
JVM.

## Validation

The replacement XML was validated against
`gear4jtest-xml/src/main/resources/assembly-line.xsd` with the JDK XML Schema
validator.

Gradle TestKit could not be executed in the delivery environment because the
Gradle 9.6.1 wrapper distribution is not available locally and the environment
cannot resolve `services.gradle.org`.

## Delivery-host command

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-gradle-xml2java:test \
  --tests '*XmlAssemblyLineGeneratorFunctionalTest'
./gradlew check
```
