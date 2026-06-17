# 0011 — Phase 3 RUN promotion and persistence batching

## Status

Accepted.

## Context

After the MVP runtime and phase 1/2 hardening passes, two long-term production-readiness items still had immediate value
without introducing a large subsystem:

- promoting an external TEST definition to RUN should not persist RUN metadata for an artifact that cannot even be
  translated and compiled;
- station-log persistence should avoid the update-then-insert round trip on dialects with a safe native upsert while
  preserving the existing finalized-log semantics.

Supply-chain verification remains intentionally opportunistic and non-blocking for this MVP.

## Decision

### RUN candidate validation

`AssemblyLineManager` now validates RUN candidates before metadata publication:

- direct RUN publication validates after storing the artifact and before inserting the RUN object;
- TEST-to-RUN promotion validates the TEST artifact before inserting the RUN object;
- failed validation raises `PolicyViolationException`, does not insert RUN metadata and does not invalidate the latest RUN
  alias.

The validation path reads the artifact, resolves the translator, translates to Java and compiles the generated source. It
intentionally does not instantiate the generated class and does not inject application dependencies.

### Station-log native upsert

`DatabaseAssemblyRunRepository` now uses native batched upsert for PostgreSQL, MySQL and MariaDB station-log writes. The
upsert updates an existing row only while the stored row is still open (`end_time IS NULL`), preserving the previous rule
that finalized logs are not overwritten.

H2 and Oracle keep the generic portable update-then-insert path. Oracle is deliberately left on the portable path until a
MERGE statement with CLOB/JSON bindings is covered by an integration test against a real Oracle-compatible database.

### Shutdown simplification

The redundant pre-check in `PersistenceFlushCoordinator.scheduleAsyncFlush(...)` was removed. The post-`markFlushScheduled`
shutdown check remains because it is the one that safely rolls back the scheduled flag during a race with shutdown.

## Consequences

- A broken external definition cannot become RUN through the manager promotion path.
- RUN validation remains side-effect light and avoids dependency injection during promotion.
- PostgreSQL/MySQL/MariaDB persistence uses fewer JDBC round trips for station-log batches.
- H2/Oracle compatibility stays conservative until integration coverage proves a native path.
