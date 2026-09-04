# ADR 0026 - Persistence shutdown uses one end-to-end deadline

## Status

Accepted — 15 July 2026.

## Context

The JDBC persistence shutdown API accepted a timeout, but the timeout previously
started only after all admitted operations had finished. Initial buffer drains
could also wait indefinitely for a per-run lock or for `DataSource#getConnection()`.
`Statement#setQueryTimeout` does not cover connection-pool acquisition and some
JDBC drivers do not react promptly to thread interruption.

A container shutdown budget must bound the calling thread even when application
or driver code is uncooperative. At the same time, Gear4J must not corrupt a
buffer by acknowledging records whose JDBC completion is uncertain.

## Decision

One monotonic deadline starts at entry to `shutdownWithReport(timeout)` and is
passed to every shutdown step:

- acquisition of the shutdown coordinator lock;
- closure of normal-operation admission and waiting for admitted operations;
- acquisition of each per-run flush lock;
- initial drains, retries and retry backoff;
- waiting for asynchronous flush workers;
- waiting for shutdown-only JDBC workers.

Shutdown JDBC batches run on owned daemon workers. The caller waits only for the
remaining budget. When a JDBC call exceeds that budget, Gear4J interrupts the
worker, restores the drained batch in memory and reports an uncertain/incomplete
shutdown. The worker never acknowledges or mutates the buffer; therefore a JDBC
call that ignores interruption may outlive the report without changing the
reported buffer state.

The shutdown report exposes `unfinishedOperations` for normal operations that
were admitted before closure but did not finish within the budget. Such
operations are not forcibly stopped. `flushExecutorShutdownStatus` distinguishes
a deliberately untouched caller-owned executor from an owned executor that
terminated or outlived the deadline. `shutdownJdbcExecutorTerminated` separately
reports a shutdown-only JDBC worker that did not terminate before the deadline.

Concurrent shutdown callers do not wait indefinitely for one another. A caller
that cannot acquire the shutdown coordinator within its own timeout receives an
`ExecutionPersistenceException`.

## Consequences

- The shutdown caller is bounded independently of pool acquisition and driver
  interruption behavior, apart from small scheduling overhead.
- A driver call may continue on a daemon thread after the report. Operators must
  treat `deadlineReached`, `unfinishedOperations`, remaining logs and executor
  shutdown statuses as the authoritative result.
- A timed-out batch is conservatively retained. The database may nevertheless
  commit it later; station-log writes are designed to be idempotent/upserted.
- The regular `jdbcStatementTimeout` still applies to statements, but pool
  acquisition limits remain a DataSource operational responsibility for normal
  runtime operations.
- Supplied executors remain caller-owned and are not shut down by Gear4J.
