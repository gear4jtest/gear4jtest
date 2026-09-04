# 2026 audit remediation — phase 2 runtime and persistence hardening

**Date:** 4 September 2026
**Baseline:** cumulative phase-1 remediation source tree
**Scope:** shared executor lifecycle, JDBC timeout boundaries, maintenance pagination and index-retention evidence

Product naming and project-wide package, module or artifact renaming remain explicitly out of scope.

## Outcome by audit finding

| Finding | Outcome | Evidence |
| --- | --- | --- |
| A-02 — process-wide default executors have unclear lifecycle | Closed for worker pools; scheduler lifecycle documented | bounded library-owned daemon pools, idle core timeout assertions and explicit caller-ownership rules |
| CFG-01 — JDBC timeout conversion can overflow | Closed | direct seconds/nanoseconds rounding and boundary regression tests |
| P-01 — unbounded OFFSET traversal | Already closed in current sources | keyset maintenance cursors, finite 10,000-item pass budgets and bounded random-page APIs |
| P-02 — simple JDBC indexes may be redundant | Not demonstrated; indexes retained | connected PostgreSQL plan selected `idx_ar_status`; supported-dialect removal evidence is absent |

## Default executor lifecycle

`ArtifactStoreExecutors` still exposes a shared executor so a default composite store does not create a pool per store.
The pool remains bounded to four workers and 512 queued tasks, rejects at saturation and never falls back to caller-thread
I/O. Its core worker can now time out after 30 seconds without work. A package-private factory makes this policy testable
without exposing or shutting down the real process-wide pool.

The default event reaction executor already used a bounded queue, daemon workers and a 60-second core timeout. A focused
test now preserves that lifecycle and its non-owning `ExecutorHandle`. Executors supplied directly by an application
remain caller-owned; executors created by the configured per-run factory remain run-owned.

Detached event cleanup keeps one process-wide single-thread daemon scheduler. Delayed cleanup must remain eligible after
the initiating execution returns, while `AssemblyLineEngine` currently has no close lifecycle. Normal completion cancels
and removes the delayed task. Making scheduled core threads time out would repeatedly recreate a worker while a far-future
deadline remains queued, so this scheduler is deliberately documented as process-scoped instead.

## Overflow-safe JDBC timeout conversion

`JdbcStatementOptions.of(Duration)` previously called `Duration.toMillis()` and then added 999 before division. Either
operation could overflow before the explicit JDBC upper-bound check, leaking `ArithmeticException` instead of the
documented argument error.

The conversion now uses `Duration.getSeconds()` and `getNano()`, rounds any fractional second upward and rejects a result
above `Integer.MAX_VALUE`. Tests cover the exact maximum, one nanosecond above it and `Duration.ofSeconds(Long.MAX_VALUE)`.

## Maintenance pagination review

No built-in unbounded offset sweep remains to replace:

- `ArtifactConsistencyChecker` reads after `OperationChainObjectCursor(publishedAt, id)` and stops after a finite budget;
- `ArtifactPublicationReconciler` reads after `OperationChainPublicationStageCursor(stagedAt, stageId)` and uses the same
  finite-pass model;
- the JDBC implementations bind those exclusive cursors with deterministic unique tie-breakers;
- remaining `LIMIT/OFFSET` APIs use validated `PageRequest` values capped by `PageRequest.MAX_LIMIT` and intentionally
  support user-facing random page access.

Adding another public cursor API without a built-in traversal consumer would increase pre-1.0 API surface without fixing
an observed defect. The implementation from
[bounded operations phase 3](remediation-2026-08-23-phase-3-bounded-operations-configuration.md) is retained unchanged.

## Index-retention decision

The simple `assembly_run(status)` index is a left-prefix candidate for removal when compared structurally with
`assembly_run(status, start_time, id)`. Structural overlap alone is insufficient evidence across PostgreSQL, MySQL,
MariaDB and Oracle. Existing connected PostgreSQL qualification selected the narrower `idx_ar_status` and completed its
small top-N sort in about one millisecond.

Removing the index now could trade lower write amplification for a read-plan regression that the audit environment cannot
measure. All V1 migrations and the baseline validator therefore remain unchanged. Reconsider removal only after a
controlled per-dialect comparison records write cost, natural read plans and latency with and without each candidate.
The governing evidence is [ADR 0037](../decisions/0037-jdbc-history-queries-require-dialect-plan-evidence.md) and the
[phase-15 SQL-plan report](remediation-jdbc-sql-plan-qualification-phase-15-2026-08-12.md).

## Required validation

Run in a connected repository:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*EventHandlingDefinitionTest'
./gradlew :gear4jtest-external-api:test --tests '*ArtifactStoreExecutorsTest'
./gradlew :gear4jtest-jdbc:test --tests '*JdbcStatementOptionsTest'
./gradlew clean check
./gradlew integrationTest dependencyCheckAggregate
```

The database matrix remains mandatory before any future index removal; this phase intentionally makes no migration
change.

## Validation in the audit environment

- The two changed production classes compile with the Java 17 `jdk.compiler` module and `-Xlint:all`.
- Standalone regression harnesses pass for bounded executor configuration, actual idle-core retirement, maximum JDBC
  timeout acceptance and deterministic oversized-timeout rejection.
- All 157 repository-local Markdown files considered by the documentation-link gate have valid local links.
- The nine Python release-tool tests pass and all shell scripts pass `bash -n`.
- Gradle/JUnit, Spotless, Checkstyle, integration, SCA and publication tasks could not start because the wrapper needs
  `gradle-9.6.1-bin.zip` from an endpoint unavailable in this environment. They remain mandatory before merge.
