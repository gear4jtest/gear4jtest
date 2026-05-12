# Core runtime architecture

## Status

Implemented, evolving.

## Summary

The core runtime executes an `AssemblyLine` through `PipelineEngine`.

The engine is responsible for:

- resolving runtime extensions;
- creating run-scoped services;
- merging default and request context;
- creating and registering the `ExecutionContext`;
- building the root runner chain;
- executing the root station;
- finalizing the run trace and shutting down the event runtime.

## Main runtime types

| Type                       | Role                                                         |
|----------------------------|--------------------------------------------------------------|
| `AssemblyLine`             | Pipeline definition.                                         |
| `RunRequest`               | Per-run input, context and runtime overrides.                |
| `ExecutionResult`          | Public result returned by execution.                         |
| `ExecutionContext`         | Mutable state of one run.                                    |
| `ExecutionServices`        | Run-scoped services used by strategies and station contexts. |
| `StationExecutionContext`  | Context exposed to station execution and user code.          |
| `PipelineEngine`           | Runtime orchestrator.                                        |
| `StationRunner`            | Executes a station through a composable runner chain.        |
| `StationExecutionStrategy` | Strategy for a specific station kind.                        |

## ExecutionContext vs ExecutionServices

Keep these concepts separate.

`ExecutionContext` is stateful and run-specific. It carries identifiers, context values, runtime contract, call stack
and execution trace.

`ExecutionServices` carries services available during a run: event manager, resource factory and station-scoped resource
registry.

This separation prevents the context object from becoming a bag of infrastructure and keeps service exposure
intentional.

## Runner chain

Station execution is composed through a runner chain.

A station runner can be wrapped for:

- lifecycle hooks;
- scope initialization;
- persistence observation;
- exception boundaries;
- terminal strategy dispatch.

Keep concerns separated. Do not move all behavior into station strategies.

## Station strategies

Strategies should execute station-specific behavior only.

Examples:

- `WorkStationStrategy` invokes operators.
- `SequenceStationStrategy` executes children in order.
- `ContainerStationStrategy` executes branches and aggregates outcomes.
- `IfElseContainerStationStrategy` chooses a branch.
- `IteratorStationStrategy` iterates and accumulates output.
- `PipelineCallStationStrategy` executes child pipelines.
- `SignalStationStrategy` produces flow signals.

## Flow decisions

Flow decisions should be based on runtime station outcomes and explicit flow configuration.

Do not use persisted logs or database records to decide whether the engine should continue, stop or fail.

## Error handling

General rules:

- Station-level exceptions should be handled at the station boundary.
- STOP and CANCEL should be modeled as flow outcomes.
- JVM `Error` should not be swallowed as an ordinary recoverable failure.
- Persistence and event failures must not silently corrupt the runtime trace.

## Pipeline calls

`PipelineCallStation` can execute a child pipeline inline or as a nested run.

A nested run creates its own execution trace and runtime setup. The current MVP inherits parent key/value context for
nested runs, but this is an explicit implementation choice that can later become a configurable context propagation
policy.

A running pipeline graph must not be mutated while a run is in progress.
