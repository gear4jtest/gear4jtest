# Phase 6 follow-up — Preserve JDBC failures at shutdown deadline

**Date:** 16 July 2026
**Scope:** `gear4jtest-jdbc` shutdown diagnostics

## Problem

`DatabaseExecutionManagerTest.shutdownWithReport_shouldKeepAndReportRecordsWhenRetriesReachDeadline` could report only:

```text
Persistence shutdown deadline reached while waiting for JDBC batch completion
```

although a previous retry had already failed with the more useful repository cause:

```text
database unavailable
```

The phase 6 deadline outcome was terminal and overwrote the last retryable JDBC exception in `ShutdownRunState`.
The buffered record was retained correctly, but the final report lost the storage failure that explained why retries
started.

## Correction

Shutdown flush outcomes now distinguish a deadline from other terminal failures.

When a deadline follows an earlier JDBC failure:

- the earlier JDBC failure remains the primary diagnostic in `PersistenceShutdownReport.RunFailure`;
- the deadline exception is retained as a suppressed exception internally;
- `PersistenceShutdownReport.deadlineReached()` still reports the timeout explicitly;
- retries stop and the buffered station logs remain available for diagnostics or recovery.

When no earlier failure exists, a pure timeout still reports the deadline message as before.

## Test hardening

The regression test is deterministic:

1. the first JDBC attempt throws `database unavailable`;
2. the second attempt signals that it started and then ignores interruption until released;
3. the shutdown reaches its 100 ms deadline;
4. the report must contain the original JDBC message and exactly two attempts.

An autonomous Java 17 harness using the real `PersistenceFlushCoordinator` produced:

```text
phase7-failure-smoke=OK attempts=2 message=database unavailable
```

## Compatibility

No public type, record component or method signature changed. The modification is limited to private shutdown outcome
and state coordination.
