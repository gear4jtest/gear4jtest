# Phase 7 follow-up — Exhaustive JDBC verification by default

**Date:** 15 July 2026
**Scope:** Gradle dialect selection, pull-request CI and release gating.

## Reason

The first phase 7 implementation supported `all`, but selected PostgreSQL by default and ran the four independent jobs
only on main, scheduled and release workflows. That allowed an ordinary local `build` and a pull-request build to pass
without proving MySQL, MariaDB and Oracle compatibility.

## Adjustment

- `gear4jDatabaseDialect` now defaults to `all`.
- Direct JUnit execution of both multi-dialect suites also defaults to `all`.
- Unknown dialect values fail in `verifyDatabaseDialectSelection` before the integration suite starts.
- The four GitHub Actions database jobs now run on pull requests as well as pushes and schedules.
- The general build/coverage and Sonar jobs select PostgreSQL explicitly to avoid duplicating the mandatory parallel
  matrix in the same workflow.
- `releaseCheck` depends on `verifyReleaseDatabaseMatrixSelection` and refuses any explicit value other than `all`.

## Resulting contract

```bash
# Exhaustive by default: PostgreSQL, MySQL, MariaDB and Oracle
./gradlew build
./gradlew check
./gradlew :gear4jtest-jdbc:integrationTest
./gradlew :gear4jtest-external-jdbc:integrationTest

# Explicit development fast path
./gradlew :gear4jtest-external-jdbc:integrationTest -Pgear4jDatabaseDialect=mysql

# Always exhaustive; a single-dialect override is rejected
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```

The property remains useful for CI parallelisation and focused diagnosis, but selecting one provider is now an explicit
opt-out from the normal repository verification contract.

## Validation performed in the preparation environment

- YAML parsing for CI, release and JReleaser configuration;
- static assertions for the `all` default and four-dialect pull-request matrix;
- direct-test fallback inspection for both JDBC suites;
- repository-local Markdown link validation;
- ZIP integrity and exclusion of generated or VCS directories.

Gradle and Testcontainers could not be executed because the environment could not resolve `services.gradle.org`.
