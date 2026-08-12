# Performance and continuous-quality gates

## Scope

Phase 6 adds reproducible guardrails rather than claiming universal capacity numbers. JMH runs in a forked Java 17 JVM
with two warmups, three measured iterations and one-second iterations. The harness lives only in `gear4jtest-core` and
uses benchmark-scoped dependencies on the optional modules; JMH is no longer applied to every published module.

The current scenarios cover:

- event subscription filtering;
- GEL evaluation with inert maps;
- Jackson payload isolation;
- generated Java source compilation;
- bounded experimental-cache hits;
- JDBC station-log batch buffering;
- streaming an 8 MiB artifact without materializing another full payload.

Run the suite and its guardrails with:

```bash
./gradlew verifyPerformanceBudgets
```

Raw JSON and human-readable reports are written under `gear4jtest-core/build/reports/jmh`.

## Budget policy

`config/performance-budgets.json` is the versioned source of truth. It checks average latency or throughput, normalized
allocation per operation, live thread count and used heap after each measured iteration. The initial values are portable
ceilings intended to detect catastrophic regression across GitHub-hosted runners; they are not production SLOs.

After the first green run on the pinned CI image, record the report with the release evidence and tighten budgets only
upward through a reviewed change. A threshold must not be relaxed without a benchmark report, cause analysis and an
explicit decision. Production sizing still requires representative payloads, databases, network latency and concurrency.

## Saturation and recovery

Unit and stress tests complement nominal JMH throughput:

- the shared event dispatcher proves non-blocking rejection at its fixed capacity and continues after task failure;
- an asymmetric two-run load proves that a quiet run reaches reaction submission after at most one 64-event loud-run
  dispatch slice, with a generous two-second portable test ceiling rather than a production SLO;
- a saturated JDBC datasource proves that the complete readiness probe returns within its configured budget envelope,
  overlapping checks fail fast and a non-cooperative acquisition occupies at most one daemon worker per runtime;
- the cache proves concurrent capacity bounds, TTL cleanup, least-recently-used eviction and mutable-output isolation;
- persistence tests retain and retry drained batches after failures.

## Coverage ratchet

`config/module-coverage-thresholds.json` contains a line-coverage floor for every published module.
`config/critical-coverage-thresholds.json` versions branch thresholds for the runtime, concurrency, persistence,
compiler, storage and Spring hot spots. `coverageVerification` combines unit and integration execution data, validates
that the policy is complete and fails on regression.

`coverageReport` remains an explicit report-generation task and is not a subproject-build finalizer. In addition to the
aggregate HTML/XML report, it writes:

- one combined XML report per module under `build/reports/jacoco/modules`;
- `build/reports/jacoco/coverage-calibration.json`, containing the observed ratios, current minimums and suggested
  minimums with two percentage points of safety margin.

Only a green calibration report produced on the pinned Java 17 CI runner may justify raising a threshold. Pull requests
cannot lower or remove an existing ratchet: CI compares both policy files with the target branch through
`scripts/verify-coverage-ratchet.py`. The initial 30% module floors remain conservative until the first connected
calibration; the priority is then to move P1 branch coverage toward 70–80% in small, test-backed increments rather than
forcing artificial 100% coverage.

## Database matrix

The unqualified JDBC integration tasks execute the complete supported matrix:

```bash
./gradlew :gear4jtest-jdbc:integrationTest
./gradlew :gear4jtest-external-jdbc:integrationTest
```

Select one dialect only when a faster diagnostic loop is explicitly required:

```bash
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=mysql
```

GitHub Actions distributes PostgreSQL, MySQL, MariaDB and Oracle across independent mandatory jobs on pull requests,
main-branch pushes, the weekly schedule and before Maven Central publication. The general build/coverage job selects
PostgreSQL explicitly to avoid repeating the complete matrix that those four jobs already enforce.

### Latest RUN index evidence

The external JDBC matrix also loads 20,000 operation-chain versions per dialect,
with TEST rows intentionally newer and more numerous than RUN rows. It then:

- verifies the migrated `idx_op_chain_latest_run` column order;
- records an `EXPLAIN ANALYZE` plan where supported and an Oracle
  `DBMS_XPLAN` plan otherwise;
- requires the selected plan to name `idx_op_chain_latest_run`;
- records the average duration of 50 prepared top-one lookups after 10 warmups.

The plan and timing are published as JUnit report entries under
`findLatestRun.<dialect>`. The timing is evidence, not a portable performance
budget: database startup, host capacity and JDBC driver behavior differ across
runners. A missing index in the plan is a regression and fails the integration
test.

### Core execution-history plan qualification

The core JDBC matrix also seeds 20,000 assembly runs and 10,000 station logs in
the already-started dialect container. Six repository reads are qualified:

- assembly-line history;
- status history;
- global history;
- root station logs;
- child station logs;
- all station logs for one run.

Each scenario verifies the repository result, performs three warmups and nine
measured executions on one connection, and then records the engine plan.
PostgreSQL uses `EXPLAIN ANALYZE`, MySQL uses `EXPLAIN ANALYZE FORMAT=TREE`,
MariaDB uses `ANALYZE FORMAT=JSON`, and Oracle combines a timed execution with
`EXPLAIN PLAN`/`DBMS_XPLAN`. H2 remains a functional test dialect and is not
reported as production plan evidence.

The connected test verifies the five ordered-index column lists directly from
JDBC metadata. The natural plan is then retained as evidence and the report
states whether the reference index was selected and whether a full scan was
observed. Those observations are deliberately not exact-plan assertions:
optimizers may legitimately choose a narrower predicate or foreign-key index,
or a scan for a small table. A two-second per-call ceiling catches catastrophic
regressions on shared CI; p50, p95 and maximum durations are evidence rather
than production SLOs. Reports are written to:

```text
gear4jtest-jdbc/build/reports/sql-plan-qualification/<dialect>.md
```

Run all four engines or select one diagnostic engine with:

```bash
./gradlew :gear4jtest-jdbc:integrationTest
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=postgresql
```
