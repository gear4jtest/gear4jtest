# Phase 6 — Bounded JDBC persistence shutdown

**Date:** 15 July 2026
**Scope:** `gear4jtest-jdbc` shutdown coordination only

## Objective

Make the timeout supplied to `DatabaseExecutionManager.shutdownWithReport(...)`
a real end-to-end budget rather than a retry-loop budget that started after
potentially unbounded waits.

## Implemented changes

- Added `PersistenceShutdownDeadline`, based on `System.nanoTime()`, and started
  it before any shutdown lock or operation wait.
- Replaced the unbounded operation-gate wait with a deadline-aware wait and
  exposed the count of admitted operations still running.
- Replaced blocking shutdown buffer-lock acquisition with `tryLock(remaining)`.
- Executed shutdown JDBC batches on owned daemon workers and bounded `Future`
  waits by the same remaining deadline.
- Restored drained records when batch completion is not confirmed before the
  deadline.
- Stopped retrying a batch whose completion is uncertain after timeout, avoiding
  concurrent duplicate attempts while the original driver call may still run.
- Added `unfinishedOperations` to `PersistenceShutdownReport`. A later audit
  correction split the overloaded executor flag into
  `flushExecutorShutdownStatus` and `shutdownJdbcExecutorTerminated`, so a
  caller-owned executor is no longer reported as if Gear4J terminated it.
- Removed method-level synchronization from the long shutdown body. Concurrent
  callers now use a timed shutdown lock.

## Tests

`DatabaseExecutionManagerTest` now covers:

1. an admitted repository write that remains blocked beyond the budget;
2. a shutdown JDBC write that deliberately ignores interruption;
3. an asynchronous flush that retains the per-run flush lock beyond the budget;
4. preservation of buffered records and accurate report fields;
5. the existing successful drain, retry and caller-owned-executor behavior.

## Autonomous validation

The modified production sources and tests compile with Java 17 using dependency
stubs. A standalone harness using the real manager and repository path produced:

```text
phase6-inflight-ms=69
phase6-jdbc-ms=67
phase6-lock-ms=71
phase6-smoke=OK
```

Both scenarios used a 60 ms budget. The excess is normal thread-scheduling and
measurement overhead; neither scenario waited for the blocked JDBC call to be
released.

## Deliberate limitation

Java/JDBC offers no portable way to forcibly terminate
`DataSource#getConnection()` or a driver call. A timed-out worker is a daemon and
may finish later. The immutable report remains conservative: the batch is
retained and worker non-termination is reported. Pool-acquisition configuration
for ordinary runtime operations remains a separate operational hardening item.

## Validation to run with Gradle

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-jdbc:test \
  --tests '*DatabaseExecutionManagerTest' \
  --tests '*PersistenceFlushCoordinatorTargetedCoverageTest'
./gradlew check
```

The audit environment could not download Gradle 9.6.1 because
`services.gradle.org` was not resolvable.
