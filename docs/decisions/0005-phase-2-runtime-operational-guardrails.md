# Phase 2 runtime operational guardrails

## Status

Implemented.

## Decision

Phase 2 adds operational guardrails without changing Gear4J into a durable messaging or workflow platform:

- public execution outcomes distinguish `SUCCEEDED`, `SKIPPED`, `FAILED`, `STOPPED` and `CANCELLED`;
- parallel containers use an engine-level default wait timeout unless a station overrides it;
- callers may provide a run-scoped `CancellationToken`, and long-running operators may cooperatively poll it;
- container execution mechanics are split into sequential and parallel executors behind the existing strategy API;
- JDBC station-log persistence has bounded buffers, periodic flushes and runtime statistics;
- caller-provided persistence executors remain caller-owned and are not shut down by Gear4J;
- persistence records and built-in event payload policies may apply a `SensitiveDataRedactor` before values leave the live runtime;
- integration tests are run through `integrationTest`, not every unit `test` invocation; database-dependent tests own their container lifecycle through Testcontainers.

## Execution outcome semantics

`ExecutionResult.isSuccess()` now means that execution completed normally without the root station being conditionally skipped. A station skipped by condition remains `SKIPPED` even when a fallback transformer supplies the replacement output needed by downstream stations. Unary skips also remain `SKIPPED` while carrying the input forward unchanged. A functional stop is reported as `STOPPED`, and a technical cancellation as `CANCELLED`; those terminal non-success states are not silently presented as success.

This makes the trace explicit: continuation output and station outcome are separate concerns. A fallback transformer can keep the flow type-safe, but it does not hide the fact that the guarded station itself did not execute.

## Run lifecycle timestamps and hook failures

The engine now marks a run as `RUNNING` and assigns `startTime` before invoking `RunLifecycleExtension.onRunStarted`. Persistence and audit hooks therefore observe the same start time that callers later see in the final run trace.

`onRunCompleted` is invoked after the run has been finalized with status, result, error, final context and end time. If a critical completion hook fails, the engine normalizes that failure into a failed `ExecutionResult` instead of leaking the raw hook exception to callers.

## Parallel execution safety

A parallel container without its own `awaitTimeout` uses `ParallelExecutionConfiguration.defaults()`. The default is finite so one blocked branch cannot keep the run waiting forever by accident.

Cancellation remains cooperative: thread interruption is attempted for submitted branch tasks, but long user operators should inspect `StationExecutionContext.getGlobalContext().getCancellationToken()` when they can stop safely.

## Persistence safety

`DatabaseExecutionManager` now exposes `PersistenceRuntimeConfiguration` and `PersistenceRuntimeStats`.

A bounded per-run station-log buffer prevents unlimited heap growth when the database is slow. Periodic flushing prevents low-volume runs from keeping records in memory until completion. Buffer saturation is treated as a persistence failure, not silently dropped observability data.

A failed station-log flush no longer discards a batch merely because it had already been drained from the in-memory queue. The manager restores drained records before surfacing the flush failure. Non-terminal asynchronous failures can be retried by a later periodic or final flush; terminal failures still mark the run buffer unhealthy.

## Sensitive data boundary

`SensitiveDataRedactor` is deliberately opt-in for compatibility. Applications storing or asynchronously forwarding sensitive runtime data should configure it before production use. The redactor runs before persistence records are written and can be wrapped around the event payload policy.

## Non-goals

This phase does not provide:

- durable events;
- distributed cancellation;
- exactly-once persistence semantics;
- automatic PII discovery;
- a retry/DLQ subsystem for failed persistence.
