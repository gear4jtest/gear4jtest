# Runtime error semantics

## Purpose

Gear4J must distinguish errors that reject an execution request from failures that occur once a run has begun.
This prevents infrastructure or user-code failures from escaping unpredictably to API consumers while preserving a complete execution trace.

## Pre-run validation failures

Validation failures occur before a pipeline run begins and before any station is executed. Examples include an invalid runtime contract, a missing mandatory dependency, an unresolved strategy, or an invalid compiled pipeline definition.

These failures may be raised directly to the caller because no runtime execution has started and no `AssemblyRunTrace` is expected to represent partial work.

## Runtime station failures

Once station execution begins, non-fatal failures must be represented through `StationLogTrace` and the resulting `ExecutionResult` rather than through an uncaught infrastructure exception.

A station failure is reduced to its terminal log status. Parent strategies inspect that status using `FlowDecider` and their configured `FlowConfig`:

- `FAIL_FAST` interrupts the current flow;
- `IGNORE_AND_CONTINUE` permits subsequent work;
- `COLLECT_AND_FAIL` continues work while preserving eventual failure.

This rule applies equally when the error originates from station processing logic or from a critical station lifecycle observer. The error origin remains observable through a diagnostic `StationLifecycleException` recorded on the station trace; it is deliberately not a new flow-policy dimension at this stage.

## Station lifecycle extensions

`StationLifecycleExtension` is intended for persistence, metrics, tracing and audit hooks.

- `BEST_EFFORT`: the hook failure is logged and does not affect the station status.
- `CRITICAL`: the hook failure marks an otherwise running, succeeded or skipped station as `FAILED`; the normal parent flow policy decides the impact on the pipeline. An already failed, stopped or cancelled terminal status is not overwritten, but the observer failure is retained as diagnostic material.

A critical `onStationStarted` failure prevents the station delegate from executing. Completion hooks are still invoked with the failed snapshot so other observers can close or persist the terminal station state.

`PersistenceExtension` is intentionally ordered last among ordinary lifecycle observers. This lets it persist the normalized terminal station status after another critical observer has changed a successful station into `FAILED`. A failure of persistence itself cannot durably record its own failure and remains a specific operational concern.

## Fatal JVM errors

Fatal `Error` instances are not normalized into station failures. They continue to escape the engine boundary.

## Open follow-up: run lifecycle hooks

`RunLifecycleExtension` currently has separate behavior and must be reviewed against the same principle, in particular for failures from `onRunStarted` and `onRunCompleted`. This is deliberately recorded as a follow-up rather than silently changing run-persistence semantics in the station-lifecycle fix.


## Public terminal outcomes

The public `ExecutionResult` exposes `SUCCEEDED`, `FAILED`, `STOPPED` and `CANCELLED`. `isSuccess()` returns true only for a normally completed run. This prevents a caller from interpreting an intentionally stopped or technically cancelled run as a completed business success.
