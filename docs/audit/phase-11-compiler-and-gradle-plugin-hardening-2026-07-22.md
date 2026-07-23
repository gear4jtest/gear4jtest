# Phase 11 — Generated compiler and Gradle XML plugin hardening

**Date:** 22 July 2026
**Baseline:** phase 10 exception-taxonomy hotfix

## Scope

This phase closes audit findings A04, A13 and A18:

- inconsistent javac/JDT fallback and parent-classloader resolution;
- repeated compilation between publication validation and runtime loading;
- XML Gradle task not proven with TestKit, configuration cache and build cache.

## Implementation

### One compiler backend per manager

`DefaultGeneratedSourceCompiler` selects javac once when `jdk.compiler` is
available, otherwise JDT. A syntax or type error from javac is returned without a
second JDT attempt. Diagnostics are wrapped with the selected backend name while
preserving the original `CompilationException` as cause.

### Java 17 and parent classloader resolution

`JavaxToolsGeneratedSourceCompiler` keeps `--release 17` and augments the process
classpath with file-based URLs found on the supplied parent-classloader chain.
Its file manager can also resolve an exact class resource directly from that
classloader. Tests compile against a class visible only from a temporary
`URLClassLoader` and reject `Thread.ofVirtual()` as a post-Java-17 API.

`JDTInMemoryCompiler` now enables `OPTION_Release` in addition to source, target
and compliance level 17. The JDT adapter remains isolated behind the stable
`GeneratedSourceCompiler` SPI.

### Bounded shared compilation cache

`AssemblyLineManager` wraps the selected compiler with a package-private
`BoundedGeneratedSourceCompiler`:

- 128 completed entries and 16 MiB of bytecode maximum;
- LRU eviction;
- SHA-256 key over class name and source bytes;
- single-flight concurrent compilation;
- defensive bytecode copies;
- failures are never cached.

The same wrapper is passed to publication validation and generated-line loading.
A manager-level test publishes and loads the same artifact and asserts a single
delegate compilation.

### Real Gradle plugin execution tests

`XmlAssemblyLineGenerateTask` no longer calls `project.delete(...)` during task
execution. `FileSystemOperations` is injected and performs output replacement
after all XML translations succeed.

`XmlAssemblyLineGeneratorFunctionalTest` uses a dependency-free signal pipeline so the nested build tests cache behavior
without relying on plugin test classes being visible as consumer operator dependencies. It uses Gradle TestKit to
prove:

- the real generation task runs successfully;
- strict configuration-cache reuse;
- build-cache restoration after deleting generated outputs;
- XML input changes rerun the task and remove obsolete outputs.

CI now runs this functional suite with configuration and build caches; the
TestKit scenarios themselves execute repeated builds to prove cache reuse, instead of
running `help` twice.

## Local validation performed

The execution environment could not resolve the Gradle wrapper distribution, so
Gradle TestKit itself was not executed here. The following independent checks
were completed:

- Java 17 compilation of all modified compiler/cache production classes using
  minimal dependency stubs;
- Java syntax compilation of the modified compiler/cache tests using JUnit and
  AssertJ stubs;
- runtime javac harness resolving a parent-only class;
- runtime javac harness rejecting a post-Java-17 API while running on JDK 21;
- runtime cache harness confirming one delegate compilation for two identical
  requests;
- YAML and local Markdown-link validation;
- archive hygiene and checksum verification.

Observed harness output:

```text
phase11-production-javac-smoke=OK
phase11-java-tests-syntax=OK
phase11-smoke=OK compilerCalls=1
phase11-release17-smoke=OK
phase11-core-external-production-compile=OK
phase11-manager-cache-test-syntax=OK
phase11-manager-cache-smoke=OK compilerCalls=1
```

## Delivery-host commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*GeneratedSourceCompilersTest' \
  --tests '*JavaxToolsGeneratedSourceCompilerTest' \
  --tests '*BoundedGeneratedSourceCompilerTest' \
  --tests '*AssemblyLineManagerCompilationCacheTest'
./gradlew :gear4jtest-gradle-xml2java:test \
  --tests '*XmlAssemblyLineGeneratorPluginTest' \
  --tests '*XmlAssemblyLineGeneratorFunctionalTest'
./gradlew check
```

The full `check` requires Docker because the default JDBC integration selection
is the complete four-dialect matrix.
