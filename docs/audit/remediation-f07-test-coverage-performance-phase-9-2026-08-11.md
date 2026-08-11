# Remediation F-07 - Test, coverage and performance conventions, phase 9

## Scope

This is the second mechanical extraction from the root `build.gradle`. It moves the shared test-suite and JaCoCo
configuration, the aggregate coverage tasks and ratchets, the JMH runtime configuration and the performance-budget
verification task into the included `build-logic` build.

The versioned policy files remain unchanged:

- `config/module-coverage-thresholds.json` owns module line-coverage ratchets;
- `config/critical-coverage-thresholds.json` owns critical-class branch-coverage ratchets;
- `config/performance-budgets.json` owns latency, throughput, allocation, live-thread and heap budgets.

## Convention boundaries

- `gear4j.test-suite`: the existing `integrationTest` convention, JaCoCo 0.8.14 instrumentation and XML unit-test
  reports.
- `gear4j.root-coverage`: `integrationCheck`, aggregate and per-module JaCoCo reports, policy validation, critical and
  module ratchets, calibration output and the existing `check` dependencies.
- `gear4j.benchmark`: JMH plugin 0.7.3 with the existing JMH 1.37 execution parameters, output files and profilers.
- `gear4j.root-performance`: the existing `verifyPerformanceBudgets` task and its versioned JSON validation.

The root build still decides which modules receive the test and benchmark conventions, declares every test and
benchmark dependency from the main version catalog, connects Sonar to `coverageReport`, and connects the unchanged
public tasks to `releaseCheck`.

## Preserved public contract

The following task names and report locations are unchanged:

- `integrationCheck`;
- `jacocoRootAllReport` and `build/reports/jacoco/report.xml`;
- every `jacocoModuleReport*` and `jacocoModuleCoverage*` task;
- `jacocoCriticalCoverageVerification`;
- `coverageCalibrationReport`, `coverageReport` and `coverageVerification`;
- `verifyCoveragePolicy` and `verifyPerformanceBudgets`;
- `gear4jtest-core:jmh`, `build/reports/jmh/results.json` and `build/reports/jmh/human.txt`.

No coverage threshold, critical class, performance budget, dependency scope or release dependency is changed.

## Regression guards

`build-logic:test` now covers two isolated TestKit fixtures without a target-project version catalog:

1. the module fixture checks Java 17, quality, `integrationTest`, JaCoCo and the complete JMH configuration;
2. the multi-project fixture checks root aggregation, public coverage/ratchet tasks, report paths, `check` wiring,
   module instrumentation and performance-budget task presence.

`buildLogicCheck` remains part of root `check`.

## Explicit non-goals

- No threshold or performance-budget recalibration.
- No change to the JDBC test matrix or Testcontainers dependency scopes.
- No OWASP, API compatibility, staging, publication or release extraction in this phase.
- No dependency locking or `verification-metadata.xml` enforcement before 1.0.
- No change to the current supply-chain residual-risk decision.
