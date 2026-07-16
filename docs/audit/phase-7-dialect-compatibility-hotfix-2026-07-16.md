# Phase 7 follow-up — PostgreSQL enum and Oracle reserved identifier

**Date:** 16 July 2026
**Scope:** `gear4jtest-external-jdbc`

## Trigger

The exhaustive Testcontainers matrix exposed two provider-specific failures:

1. PostgreSQL rejected a `VARCHAR` parameter written to the native `execution_mode` enum column.
2. Oracle rejected the `mode` column in `operation_chain_object` because `MODE` is reserved.

## Changes

### Portable schema identifier

The physical column is now named `publication_mode` for every dialect. The change is applied consistently to:

- PostgreSQL, MySQL, MariaDB, Oracle and H2 V1 migrations;
- unique constraints and the PostgreSQL partial latest-RUN index;
- repository inserts, reads, existence checks and latest-RUN lookup;
- schema validation metadata.

Gear4J is not yet in production, so the V1 migrations are intentionally corrected directly rather than adding a compatibility V2 migration. Existing local schemas created from an older V1 must be recreated or have the column renamed manually before migration checksum validation.

### PostgreSQL enum binding

`ExternalRepositorySqlDialect.bindExecutionMode(...)` now binds PostgreSQL values with:

```java
statement.setObject(index, mode.name(), Types.OTHER);
```

Other dialects keep `setString(...)`.

All parameterized mode comparisons (`insert`, `find`, `exists`) use the centralized binding.

## Regression coverage

- unit contract verifies PostgreSQL uses `Types.OTHER` and Oracle uses `setString`;
- migration contract requires `publication_mode` in every provider script;
- Oracle migration contract rejects a standalone `mode` column;
- the existing multi-dialect integration test exercises migration, publication, find, exists and latest-RUN behavior against real providers.

## Local validation performed

- Java 17 compilation of the modified repository and SQL dialect with minimal dependency stubs;
- executable JDBC proxy smoke test confirming `setObject(..., Types.OTHER)` for PostgreSQL;
- static scan confirming all V1 schemas use `publication_mode` and none defines a standalone `mode` column;
- archive integrity and documentation-link validation.

The real Testcontainers matrix could not be executed in the agent environment because Gradle 9.6.1 could not be downloaded from `services.gradle.org`.
