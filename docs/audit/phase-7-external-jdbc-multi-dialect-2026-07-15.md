# Phase 7 — External JDBC multi-dialect verification

**Date:** 15 July 2026
**Scope:** `gear4jtest-external-jdbc`, Gradle integration-test wiring, CI and release workflows.

## Objective

Close audit finding A11 by validating the external JDBC module against every production dialect rather than relying on
H2 and SQL string unit tests.

## Implementation

`ExternalJdbcMultiDialectIT` starts one selected Testcontainers database and exercises the full external persistence
contract in that container:

- initial and idempotent schema migration;
- migration-history checksum recording and mismatch rejection;
- configuration JSON upsert/read/update;
- atomic object-and-tag publication;
- idempotent retry and conflicting retry handling;
- rollback when a later tag insert fails;
- object and tag pagination with dialect-specific SQL binding;
- latest RUN lookup;
- 256 KiB streaming BLOB write, duplicate write and readback.

The tests select dialects through `gear4j.test.databaseDialect`. Gradle maps
`-Pgear4jDatabaseDialect=<dialect>` to that property for both `gear4jtest-jdbc` and `gear4jtest-external-jdbc`. The default
is now `all`, so an unqualified `build`, `check` or JDBC `integrationTest` validates every supported production dialect.
A single dialect remains available as an explicit fast path.

The CI pull-request/main/scheduled matrix and the release gate execute both JDBC modules for PostgreSQL, MySQL,
MariaDB and Oracle, and upload reports for both modules. The general coverage job selects PostgreSQL explicitly to avoid
repeating the complete matrix, while `releaseCheck` rejects any explicit selection other than `all`.

## Follow-up: exhaustive-by-default policy

After review, the selection policy was strengthened:

- `all` is the default for Gradle and for direct JUnit execution;
- pull requests run all four mandatory matrix jobs;
- the general CI build explicitly selects PostgreSQL only because the parallel matrix provides the exhaustive gate;
- `releaseCheck` refuses a single-dialect override and therefore cannot pass without the complete matrix.

## Deliberate boundaries

- Existing H2 integration tests remain as fast provider and migration checks.
- The phase validates the current offset pagination contract. Index redesign and keyset pagination remain a later
  performance phase.
- Container versions match the existing core JDBC matrix: PostgreSQL 16, MySQL 8.4, MariaDB 11.4 and Oracle XE 21.

## Verification commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-jdbc:integrationTest
./gradlew check
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```

For a focused diagnostic run only:

```bash
./gradlew :gear4jtest-external-jdbc:integrationTest -Pgear4jDatabaseDialect=mysql
```

The execution environment used to prepare this phase could not download Gradle 9.6.1 because
`services.gradle.org` was not resolvable. Dynamic container execution therefore remains to be confirmed in the user's
Docker-enabled environment.

## Follow-up discovered by the matrix

The first real PostgreSQL and Oracle runs exposed provider-specific defects in native enum binding and the reserved Oracle identifier `MODE`. They are corrected in `phase-7-dialect-compatibility-hotfix-2026-07-16.md`.
