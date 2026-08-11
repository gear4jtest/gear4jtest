# Remediation F-07 - Build conventions, phase 8

## Scope

This is the first mechanical extraction from the root `build.gradle`. It moves the shared Java 17, archive,
Spotless, Checkstyle and `integrationTest` configuration into an included `build-logic` build. Test, JaCoCo, JMH,
security, API-compatibility, staging and release orchestration remain in the root build for the next two F-07
increments.

## Convention boundaries

- `gear4j.root-quality`: repository-level Spotless targets and the root `spotlessCheck` gate.
- `gear4j.java-base`: Java 17 toolchains and `--release`, compiler flags, launchers, reproducible archives, Javadoc,
  legal JAR resources and the common test-task configuration.
- `gear4j.java-library`: `java-library`, the published module name and the library-specific test-task configuration.
- `gear4j.quality`: Spotless and Checkstyle configuration, including the existing formatter and report policy.
- `gear4j.integration-test`: the existing source set, dependency inheritance and `integrationTest` task contract.

The root project still selects which modules receive each convention and remains the aggregation point for `check`,
coverage, database matrices and release tasks. It also owns dependency declarations from the main `libs` catalog;
convention plugins do not depend on target-project catalog availability during early cross-project application. No
task name, dependency scope, threshold or publication behavior is redesigned in this increment.

## Regression guard

`build-logic:test` applies all five conventions to an isolated TestKit fixture with no target version catalog. It
checks the former root-build contract: Java 17 compilation and launch, compiler flags, archive reproducibility, module
manifest, quality plugins and reports, conventional integration-test directories, configuration inheritance and the
public `integrationTest`/`spotlessCheck` task wiring. Root `buildLogicCheck` makes this fixture part of `check`.

The main build keeps the common JUnit/AssertJ and library-specific Logback/Mockito declarations at the root, using its
generated `libs` accessor as their single version source.

The existing root `verifyJava17AndArchiveConfiguration` task remains enabled and continues checking every module after
the extraction.

## Explicit non-goals

- No dependency locking or `verification-metadata.xml` enforcement before 1.0.
- No test/JaCoCo/JMH extraction in this phase.
- No OWASP, API compatibility, staging, publication or release extraction in this phase.
- No change to the current supply-chain residual-risk decision.
