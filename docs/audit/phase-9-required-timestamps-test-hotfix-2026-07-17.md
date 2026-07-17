# Phase 9 hotfix — Required timestamp test fixtures

**Date:** 17 July 2026

## Problem

The phase 9 model validation made `OperationChainObject.createdAt` and
`OperationChainObject.publishedAt` mandatory. This matches every external JDBC V1 schema, where
`created_at` and `published_at` are declared `NOT NULL`.

Two JDBC unit tests still mocked result-set rows with null timestamps. The repository mapper then
correctly constructed the stricter domain object, which rejected those impossible rows with:

```text
java.lang.NullPointerException: createdAt must not be null
```

## Resolution

No production invariant was weakened.

- `OperationChainObjectRepositoryJdbcBehaviorTest` now supplies valid timestamps to the
  `findLatestRun` fixture and verifies both mapped values.
- `OperationChainObjectRepositoryJdbcTest` now verifies mapping of required timestamps instead of
  expecting null values.
- The invalid-hash test now uses otherwise valid timestamps so that it isolates the hash invariant.
- `OperationChainModelValidationTest` explicitly verifies that null `createdAt` and `publishedAt`
  values are rejected.

## Validation

- The five external JDBC migrations were checked: both columns are `NOT NULL` for H2, PostgreSQL,
  MySQL, MariaDB and Oracle.
- A Java 17 smoke test confirmed timestamp preservation and rejection of null timestamp values.
- No production source file or database migration was changed.

Gradle tests could not be executed in the delivery environment because the Gradle 9.6.1 wrapper
distribution could not be resolved from `services.gradle.org`.

## Recommended verification

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test --tests '*OperationChainModelValidationTest'
./gradlew :gear4jtest-external-jdbc:test \
  --tests '*OperationChainObjectRepositoryJdbcBehaviorTest' \
  --tests '*OperationChainObjectRepositoryJdbcTest'
./gradlew check
```
