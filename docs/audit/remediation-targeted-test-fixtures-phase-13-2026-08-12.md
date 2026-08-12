# Targeted test fixtures - phase 13

**Date:** 12 August 2026
**Scope:** JDBC execution-manager and generated-loader tests

## Objective

Apply the phase-2 audit-roadmap recommendation to reduce the density of the two
explicitly identified test hotspots without changing production code, test
scenarios or contract assertions.

## Changes

- `DatabaseExecutionManagerTest` is split into focused configuration/buffering
  and recovery/shutdown scenario classes.
- `DatabaseExecutionManagerTestFixture` owns the shared manager factory,
  polling assertions, records, JSON codec and mutable-payload doubles.
- `GeneratedAssemblyLineLoaderTestFixture` owns the generated-source samples,
  loader assembly, polling helpers and coordinated registry doubles.
- The largest affected scenario class is reduced from 1,183 lines to 625
  lines; the generated-loader scenario class is reduced from 740 to 529 lines.

## Contract preservation

All 23 JDBC execution-manager tests and all 10 generated-loader tests retain
their names and byte-for-byte identical method bodies. No assertion, timeout,
fixture value, production source, public API, build task or coverage threshold
changes in this phase.

## Validation

The focused and complete validation commands are:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-jdbc:test --tests '*DatabaseExecutionManager*Test'
./gradlew :gear4jtest-external-api:test --tests '*GeneratedAssemblyLineLoaderTest'
./gradlew check
```
