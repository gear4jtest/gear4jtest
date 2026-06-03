# Kernel-driven cancellation — future direction

## Status

Not implemented yet.

This note captures the preferred future direction for cancelling a running Gear4J pipeline, especially once a BO can request cancellation of an execution already in progress.

## Problem

A simple cancellation token is useful, but it should not lead to `if cancelled` checks scattered through every strategy.

Structural strategies such as sequences, iterators, containers and nested pipeline calls currently drive their own Java loops. If cancellation is handled locally by each strategy, the runtime becomes harder to reason about and every new structural strategy must remember to implement cancellation rules correctly.

There is also a second problem: a user worker may run for a long time and not return control to the strategy quickly. Gear4J should be able to stop progressing the pipeline and finalize the run after a configured timeout, even if some in-process Java code does not cooperate immediately.

## Principle

Cancellation should be controlled by a central execution spine, not by duplicated checks in every strategy.

The target architecture is composed of three layers:

1. **ExecutionKernel**
   - owns the execution agenda;
   - advances frames such as stations, sequences, iterators, containers and nested pipeline calls;
   - stops scheduling new frames as soon as run cancellation is requested.

2. **ExecutionSupervisor**
   - owns the run control state;
   - receives cancellation commands from API, BO, infrastructure or future durable control plane;
   - applies the cancellation policy;
   - exposes the execution state for observability.

3. **ActivityRegistry**
   - tracks currently running interruptible activities;
   - examples: current station thread, branch futures, nested run handles, external call handles;
   - allows the supervisor to interrupt known work without mutating kernel internals directly.

## Cancellation scopes

Gear4J should distinguish local cancellation from global run cancellation.

- `BRANCH` cancellation is local to a container branch or sub-task and may remain governed by `FlowConfig.cancelPolicy()`.
- `RUN` cancellation is authoritative and must not be ignored by flow policies.

A BO/user cancellation request is always `RUN` scoped.

## Staged cancellation policy

A future policy could look like:

```java
public record CancellationPolicy(
        Duration gracefulTimeout,
        Duration interruptTimeout,
        Duration abandonAfter,
        HardCancelMode hardCancelMode) {
}
```

The semantic phases would be:

1. **Soft cancellation**
   - mark the run as cancellation requested;
   - wake the kernel;
   - stop scheduling new frames;
   - let already running activities finish gracefully for a short duration.

2. **Interrupt cancellation**
   - interrupt registered threads;
   - cancel registered futures;
   - propagate cancellation to nested pipeline handles;
   - stop owned executors only when Gear4J owns their lifecycle.

3. **Logical abandon**
   - after the configured timeout, finalize the run as `CANCELLED` even if a non-cooperative in-process worker is still running;
   - discard late results;
   - ignore late events and station completions for the abandoned run.

4. **Hard cancellation**
   - only available when the worker is isolated behind a process/container/JVM boundary;
   - not guaranteed for arbitrary in-process Java code.

## Important limitation

In-process Java code cannot be safely killed unconditionally. Gear4J can interrupt known work and abandon late results, but it cannot safely force-stop arbitrary user code without risking corrupted in-memory state.

Hard cancellation is guaranteed only across an isolation boundary.

## Product-level promise

The BO-facing promise should be:

> A cancellation request immediately stops Gear4J from scheduling new work. Running activities are asked to stop, then interrupted. After the configured timeout, the run is finalized as `CANCELLED` and late results are ignored. Physical termination of non-cooperative user code requires isolated workers.

## Non-goal for now

This note is not a commitment to rework the runtime immediately. It should guide the future cancellation chantier when BO-driven execution control becomes a priority.
