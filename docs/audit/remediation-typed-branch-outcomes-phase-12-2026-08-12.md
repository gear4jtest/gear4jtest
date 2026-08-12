# Typed branch outcomes - phase 12

**Date:** 12 August 2026
**Scope:** internal container branch execution state

## Objective

Complete the F-02 follow-up by removing `null` as an internal control signal for
parallel branch outcomes, future resolution and container interruption. This is
an internal refactoring: the public container DSL and its output semantics stay
unchanged.

## Changes

- `ParallelBranchOutcome` now pairs its state with an explicit optional
  terminal trace and validates that only terminal states carry a trace.
- Future polling, completion races, timeouts and cooperative cancellation use
  `Optional<StationLogTrace>` instead of nullable branch results.
- `ContainerExecutionAggregation` exposes an optional interrupting child and
  provides explicit `completed` and `interrupted` factories.

## Compatibility

`ContainerResults` remains unchanged. A branch may still legitimately expose a
`null` output when it was skipped, cancelled, failed or itself returned `null`.
This phase only removes ambiguous `null` sentinels from the internal execution
protocol; it does not change public output values, flow decisions, ordering,
traces or lifecycle events.

## Validation

The focused and complete validation commands are:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*ParallelContainerBranchExecutorTest' \
  --tests '*ParallelBranchOutcomeTest' \
  --tests '*ContainerExecutionAggregationTest'
./gradlew check
```
