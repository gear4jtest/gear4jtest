# Phase 5 implementation report: JDBC operation admission

**Date:** 2026-07-14
**Scope:** remove the manager-wide lifecycle monitor from normal JDBC I/O without
changing public APIs or the per-run buffering contract.

## Implemented changes

### Short lifecycle gate

A new package-private `PersistenceOperationGate` replaces the synchronized
`executeWhileOpen(...)` callback. Its lock protects only:

1. the atomic open-state check and in-flight increment;
2. the in-flight decrement and idle signal;
3. shutdown admission closure and waiting for the counter to reach zero.

The callback itself runs outside the gate lock. Repository inserts, explicit
flushes, complete run drains and final run updates are no longer serialized across
unrelated runs by `PersistenceFlushCoordinator`.

### Shutdown admission semantics

Shutdown now closes admission before waiting for operations that already entered
the gate. This preserves the previous safety boundary:

- admitted operations complete before the shutdown buffer snapshot;
- new operations fail with `DatabaseExecutionManager is already shut down`;
- liveness changes to shut down as soon as admission closes;
- an interrupt observed while waiting is restored and included in the shutdown
  report.

The shutdown deadline remains outside this wait and is intentionally deferred to
phase 6.

### Per-run ordering retained

`OperationRecordBuffer.flushLock` is unchanged. Appends, drains, restores and final
flushes for one run remain serialized, while unrelated run operations can progress
concurrently.

## Regression tests

`DatabaseExecutionManagerTest` now includes deterministic tests proving that:

- two independent `start(...)` calls can enter blocking repository writes at the
  same time;
- shutdown closes admission before waiting for a previously admitted blocking
  write;
- the admitted write completes before the shutdown snapshot;
- a new write is rejected while shutdown is waiting.

No timing loop is used to create the concurrency window; latches control each
transition.

## Validation

Completed in the available environment:

- the changed coordination cluster and the real `DatabaseExecutionManager` compiled
  with `javac --release 17` and minimal dependency stubs;
- the real `PersistenceOperationGate` passed an autonomous two-operation/shutdown
  concurrency harness (`phase5-gate-smoke=OK maxConcurrentOperations=2`);
- the real `DatabaseExecutionManager` passed an autonomous repository-level harness
  (`phase5-manager-smoke=OK maxConcurrentJdbcWrites=2`);
- no line over 120 characters in changed Java files;
- YAML and local Markdown links validated;
- archive checked for generated build and VCS directories.

Not executable in the audit environment:

```text
./gradlew :gear4jtest-jdbc:test --tests '*DatabaseExecutionManagerTest' \
  --tests '*PersistenceFlushCoordinatorTargetedCoverageTest'
./gradlew spotlessApply
./gradlew check
```

The wrapper attempted to download Gradle 9.6.1 but DNS resolution for
`services.gradle.org` failed with `UnknownHostException`.

## Deferred to phase 6

- include waiting for admitted operations in the supplied shutdown deadline;
- use deadline-aware per-buffer lock acquisition;
- align connection-acquisition and statement timeouts with remaining shutdown
  budget;
- stop initial shutdown draining as soon as the deadline is exhausted.
