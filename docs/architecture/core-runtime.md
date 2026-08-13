# Core runtime architecture

## Status

Implemented, evolving.

## Summary

Applications execute an `AssemblyLine` through the public `AssemblyLineExecutor` contract.

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
| `AssemblyLine`             | AssemblyLine definition.                                         |
| `RunRequest`               | Per-run input, context and runtime overrides.                |
| `ExecutionResult`          | Public result returned by execution.                         |
| `ExecutionContext`         | Mutable state of one run.                                    |
| `ExecutionServices`        | Run-scoped services used by strategies and station contexts. |
| `StationExecutionContext`  | Context exposed to station execution and user code.          |
| `AssemblyLineExecutor`       | Stable public execution entry point.                         |
| `AssemblyLineEngine`           | Internal default runtime implementation.                     |
| `StationRunner`            | Executes a station through a composable runner chain.        |
| `StationExecutionStrategy` | Strategy for a specific station kind.                        |

## ExecutionContext vs ExecutionServices

Keep these concepts separate.

`ExecutionContext` is stateful and run-specific. It carries identifiers, context values, runtime contract, call stack
and execution trace.

`ExecutionServices` carries services available during a run: the stable `EventPublisher` capability, resource factory
and station-scoped resource registry. The concrete event runtime remains internal.

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
- `AssemblyLineCallStationStrategy` executes child pipelines.
- `SignalStationStrategy` produces flow signals.

Station definitions are immutable after construction. Optional behavior such as processors, error policies, skip rules,
metadata, flow configuration, container timeouts and synthetic/root flags must be provided through builders or
constructors. Runtime state belongs in `ExecutionContext`, `StationExecutionContext` and trace objects, never in the
station graph itself.

Container branches must have explicit, stable branch identifiers. Branch ids are used as functional keys for sibling
outcomes in sequential containers, not only as trace labels. The runtime must not generate random branch ids, because
branch ids need to remain deterministic across runs, tests, logs and future BO visualizations.

### Container execution contract

Sequential containers visit branches in declaration order. A branch result is
normalized before the next sibling condition or `FlowConfig` decision is
evaluated. `FAIL_FAST` stops before the next branch,
`IGNORE_AND_CONTINUE` preserves the result slot and proceeds, and
`COLLECT_AND_FAIL` proceeds before failing the container with the first
collected error. Sibling-aware conditions are therefore supported only in
sequential containers.

Parallel containers submit every eligible branch to the caller-supplied
executor and consume completions as they arrive, while exposing results in
declaration order. The effective await timeout starts after submission and
bounds only the completion wait. Timeout and fail-fast cancellation call
`Future.cancel(true)` for pending work, but a user task that ignores interruption
may continue after the container returns. A branch that completed during the
cancellation race wins over a synthetic cancellation outcome.

Container executors are always caller-owned. Neither normal completion,
fail-fast, timeout nor executor decoration shuts them down. An
`ExecutorWrapperExtension` must likewise return a non-owning view.

## Flow decisions

Flow decisions should be based on runtime station outcomes and explicit flow configuration.

Do not use persisted logs or database records to decide whether the engine should continue, stop or fail.

## Error handling

General rules:

- Station-level exceptions should be handled at the station boundary.
- STOP and CANCEL should be modeled as flow outcomes.
- JVM `Error` should not be swallowed as an ordinary recoverable failure.
- Persistence and event failures must not silently corrupt the runtime trace.

## Cancellation checkpoints

Gear4J checks run cancellation at every station entry, before parallel branch
submission and while waiting for parallel completions. Sequential, iterator,
if/else and inline-call strategies re-enter the station runner at each child or
item boundary, so they inherit the station-entry checkpoint.

Application code needs an explicit checkpoint only while Gear4J cannot regain
control: long operator/processor loops, blocking conditions or item resolvers,
external I/O and custom retry/backoff loops. Such code should call
`StationExecutionContext.getGlobalContext().getCancellationToken()` and either
poll `isCancellationRequested()` or invoke
`throwIfCancellationRequested()`, while also respecting thread interruption and
its own I/O timeout.

## AssemblyLine calls

`AssemblyLineCallStation` can execute a child pipeline inline or as a nested run.

A nested run creates its own execution trace and runtime setup. User context propagation is controlled by
`ContextPropagationPolicy` on `AssemblyLineExecutorBuilder`. The default remains the historical shallow map copy: the
child receives a distinct context map, but mutable values inside that map are still shared references. Configure
`ContextPropagationPolicy.none()`, `includeKeys(...)` or `copyValues(...)` when nested-run isolation matters.

Nested runs intentionally share the parent cancellation token and call stack so cancellation and cycle/depth protection
propagate through the call tree. Independent top-level runs should not reuse those objects accidentally; use
`RunRequest.toIndependentBuilder()` when a request is used as a reusable template.

`RunRequest.Builder.input(...)` returns an independently copied builder when it narrows the input type. This preserves
the fluent inferred-type form while preventing a broader mutable builder alias from corrupting an already narrowed
request.

A running pipeline graph must not be mutated while a run is in progress.

Assembly-line default context and `RunRequest` context are merged into a new map
for every execution. Values remain shallow references by default for compatibility.
Applications that place mutable lists, maps or DTOs in these contexts should set
`AssemblyLineExecutors.builder().initialRunContextPolicy(ContextPropagationPolicy.copyValues(...))`
to make defensive per-run copies. This policy is independent from
`nestedRunContextPropagationPolicy`, which controls the parent-to-child boundary.
