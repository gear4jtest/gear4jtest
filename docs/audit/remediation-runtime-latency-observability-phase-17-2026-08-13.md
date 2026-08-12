# Runtime latency observability - phase 17

**Date:** 13 August 2026
**Scope:** parallel-branch outcomes/duration and persistence-flush distributions

## Objective

Close the first actionable long-term observability gap remaining after audit
acceptance scenario 12: expose bounded parallel-branch signals and real
persistence-flush latency distributions without coupling the optional
Micrometer module to engine or JDBC internals.

## Delivered runtime contract

- `Gear4jMicrometerExtension` derives branch start, completion and duration from
  the stable station lifecycle snapshots. Synthetic skip, cancellation,
  interruption and failed-before-start callbacks contribute terminal outcomes.
- `gear4j.branches.rejected` increments only for an executor
  `RejectedExecutionException` before branch start.
- `PersistenceRuntimeMonitor` now offers a removable flush-observation
  subscription. Its observation contains only monotonic duration plus closed
  trigger/outcome enums.
- `PersistenceFlushCoordinator` observes asynchronous, explicit, terminal and
  shutdown attempts. Async latency includes executor queue time; empty no-op
  flush calls are excluded, while admission rejection is recorded.
- `PersistenceMetricsBinder` records
  `gear4j.persistence.flush.duration{trigger,outcome}` with percentile
  histograms enabled. Spring closes the subscription with its registrar bean.
- Callback isolation tests prove that an observer runtime exception neither
  fails persistence nor prevents the next observer from receiving the outcome.

## Cardinality and concurrency boundary

The default branch meters do not contain branch, operation or pipeline IDs.
Completion and duration use only the finite `status`; selected identifiers still
require `Gear4jMeterTagPolicy.allowlistedIdentifiers(...)`. The persistence
timer uses only framework-owned `trigger` and `outcome` values.

JDBC work and buffer locks finish before an observation is delivered. The
observer list is safe for concurrent subscriptions and flushes, each observer is
isolated independently, and the subscription can be removed without shutting
down the manager.

## Validation commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*PersistenceFlushObservationTest'
./gradlew :gear4jtest-jdbc:test \
  --tests '*PersistenceFlushCoordinatorTargetedCoverageTest'
./gradlew :gear4jtest-micrometer:test \
  --tests '*Gear4jMicrometerExtensionTest' \
  --tests '*PersistenceMetricsBinderTest'
./gradlew :gear4jtest-spring-boot-starter:test
./gradlew check
```

## Sandbox validation status

The configured Gradle 9.6.1 distribution is not cached in the audit sandbox, so
the connected commands above remain the merge gate. Offline Java 17 validation:

- parses all 824 repository Java sources;
- compiles the complete core and JDBC production trees against the available
  Jackson/SLF4J APIs;
- compiles the changed Micrometer production classes against minimal
  API-compatible signatures;
- runs an executable metrics harness covering ordinary/synthetic/rejected
  branches, flush trigger/outcome tags and subscription removal; and
- checks documentation links, ADR identifiers, living-document metadata,
  trailing whitespace and final newlines.

This phase adds no dependency lockfile, Gradle verification metadata, durable
event delivery, spool replay or database migration. Those explicit post-1.0
decisions remain unchanged.
