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
- the cache proves concurrent capacity bounds, TTL cleanup, least-recently-used eviction and mutable-output isolation;
- persistence tests retain and retry drained batches after failures.

## Coverage ratchet

`config/critical-coverage-thresholds.json` versions branch thresholds for `EventManager`, `WorkStationStrategy`,
`JdbcSchemaMigrator` and `AssemblyLinePublicationService`. `coverageVerification` combines unit and integration execution
data and fails on regression. `coverageReport` remains an explicit report-generation task and is not a subproject-build
finalizer.

## Database matrix

The JDBC multi-dialect integration test starts only the selected container. PostgreSQL is the default on pull requests:

```bash
./gradlew :gear4jtest-jdbc:integrationTest
```

Select one dialect or the complete local matrix with:

```bash
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=mysql
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=all
```

GitHub Actions distributes PostgreSQL, MySQL, MariaDB and Oracle across independent jobs on main-branch pushes, on the
weekly schedule and before Maven Central publication.
