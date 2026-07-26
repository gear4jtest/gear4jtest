# Remediation R12 — Incremental hotspot decomposition

**Date:** 25 July 2026

## Scope

R12 starts the F-03 decomposition requested by the 23 July audit. It separates lifecycle, shutdown-I/O and migration
infrastructure responsibilities without changing public APIs, persistence semantics, database schema or event-delivery
guarantees.

## Extracted responsibilities

- `EventRuntimeShutdown` owns event shutdown mode, the single monotonic deadline and reaction-executor ownership.
- `PersistenceShutdownWriter` owns shutdown-only JDBC workers, bounded batch writes and executor termination.
- `PersistenceShutdownRunState` owns per-run retry, backoff and failure retention.
- `MigrationLockStore` owns the portable migration lock table and row acquisition.
- `MigrationResourceLoader` owns migration-list parsing, resource loading and SHA-256 calculation.
- The R8 `MigrationHistoryStore` remains the sole owner of migration-history SQL and state transitions.

`EventManager`, `PersistenceFlushCoordinator` and `JdbcSchemaMigrator` remain the public or package-level orchestrators.
The extracted classes are package-private so the refactoring does not enlarge the supported API surface.

## Characterization and ratchets

Dedicated tests cover executor ownership and timeout, retry-cause retention, saturating time arithmetic, migration list
parsing and checksums. Existing event, persistence-shutdown and multi-dialect migration tests continue to exercise the
end-to-end behavior.

The five extracted components have been added to the critical-class branch policy at the existing 50% floor. This avoids
artificially improving the parent-class ratios by moving untested branches into untracked helpers. Numeric increases
still require a connected green `coverage-calibration.json`.

## Qualification

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test :gear4jtest-jdbc:test
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=all
./gradlew coverageVerification coverageReport
./gradlew check
```

This is a first decomposition step, not a claim that every large class is finished. Future changes should continue only
where characterization tests identify a stable internal responsibility; line count alone is not a reason to introduce
another abstraction.
