# Phase 2 runtime operational guardrails

## Status

Implemented.

## Decision

Phase 2 adds operational guardrails without changing Gear4J into a durable messaging or workflow platform:

- public execution outcomes distinguish `SUCCEEDED`, `FAILED`, `STOPPED` and `CANCELLED`;
- parallel containers use an engine-level default wait timeout unless a station overrides it;
- callers may provide a run-scoped `CancellationToken`, and long-running operators may cooperatively poll it;
- container execution mechanics are split into sequential and parallel executors behind the existing strategy API;
- JDBC station-log persistence has bounded buffers, periodic flushes and runtime statistics;
- caller-provided persistence executors remain caller-owned and are not shut down by Gear4J;
- persistence records and built-in event payload policies may apply a `SensitiveDataRedactor` before values leave the live runtime;
- Docker-backed tests are run through `integrationTest`, not every unit `test` invocation.

## Execution outcome semantics

`ExecutionResult.isSuccess()` now means that execution completed normally. A functional stop is reported as `STOPPED`, and a technical cancellation is reported as `CANCELLED`; neither is silently presented as success.

This aligns the public result with `ExecutionStatus` already stored in execution traces.

## Parallel execution safety

A parallel container without its own `awaitTimeout` uses `ParallelExecutionConfiguration.defaults()`. The default is finite so one blocked branch cannot keep the run waiting forever by accident.

Cancellation remains cooperative: thread interruption is attempted for submitted branch tasks, but long user operators should inspect `StationExecutionContext.getGlobalContext().getCancellationToken()` when they can stop safely.

## Persistence safety

`DatabaseExecutionManager` now exposes `PersistenceRuntimeConfiguration` and `PersistenceRuntimeStats`.

A bounded per-run station-log buffer prevents unlimited heap growth when the database is slow. Periodic flushing prevents low-volume runs from keeping records in memory until completion. Buffer saturation is treated as a persistence failure, not silently dropped observability data.

## Sensitive data boundary

`SensitiveDataRedactor` is deliberately opt-in for compatibility. Applications storing or asynchronously forwarding sensitive runtime data should configure it before production use. The redactor runs before persistence records are written and can be wrapped around the event payload policy.

## Non-goals

This phase does not provide:

- durable events;
- distributed cancellation;
- exactly-once persistence semantics;
- automatic PII discovery;
- a retry/DLQ subsystem for failed persistence.
