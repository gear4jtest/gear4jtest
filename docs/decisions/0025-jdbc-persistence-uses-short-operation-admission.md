# ADR 0025: JDBC persistence uses short operation admission

- Status: Accepted
- Date: 2026-07-14

## Context

`PersistenceFlushCoordinator.executeWhileOpen(...)` previously used a synchronized
method and ran the supplied callback while holding the coordinator monitor. The
callbacks include run inserts, station-log flushes and final run updates. One slow
JDBC call therefore serialized every normal persistence operation, even when the
operations belonged to unrelated runs and the datasource could serve them in
parallel.

The same monitor also acted as the shutdown admission boundary: shutdown could not
start until the current callback returned, and operations could not enter after
shutdown acquired the monitor. Removing the monitor must preserve that atomic
admission guarantee and allow shutdown to observe a stable set of already admitted
operations.

## Decision

Use a dedicated package-private `PersistenceOperationGate` with:

- a volatile open/closed admission state;
- a `ReentrantLock` and `Condition` protecting only admission and the in-flight
  operation counter;
- an enter/run/finally-leave protocol where the user operation runs after the
  lifecycle lock has been released;
- shutdown closure that atomically rejects new operations and waits until every
  operation admitted before closure has left the gate.

`DatabaseExecutionManager.start`, `append`, `appendAll`, `flush` and `end` continue
to use `executeWhileOpen(...)`, but repository and buffer work now happens outside
the lifecycle lock. Per-run `OperationRecordBuffer.flushLock` remains responsible
for serializing append/drain/restore operations on one run.

Shutdown marks admission closed before it waits. Liveness therefore reports shut
down immediately, newly arriving operations fail with the existing
`ExecutionPersistenceException`, and the shutdown drain snapshot is taken only
after already admitted operations finish.

## Consequences

- Slow JDBC work for one run no longer blocks independent normal operations through
  a manager-wide monitor.
- Repository calls may now be concurrent. The built-in JDBC repository is designed
  around per-call connections; custom repositories and supplied data sources must
  be thread-safe.
- Operations on the same run remain coordinated by the per-run buffer lock where
  ordering and batch restoration require it.
- Shutdown still waits for admitted operations, preserving the former lifecycle
  boundary without holding a lock during their work.
- This decision does not make shutdown duration a hard end-to-end bound. The
  current deadline starts after admitted operations become idle, and blocking
  buffer/JDBC acquisition is handled in phase 6.

## Rejected alternatives

### Keep the synchronized callback

Rejected because it makes datasource pool size and flush worker count irrelevant
for normal operations and lets one stalled JDBC call block unrelated runs.

### Release the monitor without tracking admitted operations

Rejected because shutdown could snapshot and remove buffers while a previously
accepted operation was still creating, appending to or finalizing one of them.

### Hold a read lock for the entire operation

Rejected because it still introduces a shared lifecycle lock around JDBC I/O and
does not improve the central contention problem.
