# Phase 3 — Concurrent execution invariants

**Date:** 14 July 2026
**Scope:** parallel branch cancellation, active execution registry and container build-time validation

## Objective

Preserve the exact result of parallel branches when completion races with
cancellation, prevent active execution contexts from being silently replaced,
and reject statically invalid parallel container configurations before first
traffic.

## Changes

### Completed branch results win cancellation races

`ParallelContainerBranchExecutor` now applies one cancellation protocol to
cooperative cancellation, timeout, sibling interruption and unexpected wait
interruption:

1. resolve an already completed future before cancelling it;
2. call `Future.cancel(true)` only while it is still unresolved;
3. when cancellation returns `false`, resolve the future again because
   completion may have won between the first check and the cancellation call;
4. create a synthetic cancellation/timeout trace only when no completed result
   can be harvested.

A deterministic executor-backed regression test completes the branch from
inside `Future.cancel(...)` and returns `false`. This reproduces the exact race
without timing loops and proves that the successful result and output are
preserved.

### Active execution identifiers are unique

`ExecutionContextRegistry.register` now uses `putIfAbsent`. Registering a
different active context with the same execution identifier throws an
`IllegalStateException` and leaves the original mapping intact.

A conditional `remove(executionId, expectedContext)` operation was added.
`AssemblyLineRunCleanup` uses it so stale cleanup cannot remove a newer context
that happens to use the same identifier. The unconditional removal method is
retained for existing internal callers.

### Invalid parallel configurations fail at construction

`ContainerBaseStation` construction now rejects:

- parallel mode without an executor service;
- zero or negative station-level await timeouts;
- sibling-dependent branch conditions in parallel mode.

`ContainerStationStrategy` retains its execution-time sibling-condition check
as a secondary guard against custom or deserialized station implementations.
Engine-level default timeout validation remains unchanged.

## Tests

Added or extended tests cover:

- completion winning the cancellation race;
- duplicate active execution identifiers;
- expected-context conditional removal;
- stale run cleanup preserving a replacement context;
- sibling conditions rejected in parallel mode;
- non-positive await timeout rejected at build time;
- parallel mode rejected when no executor is supplied.

## Compatibility

The registry change intentionally converts silent corruption into fail-fast
behavior. Custom `IdGenerator` implementations must not reuse an identifier
while its previous execution remains active.

Parallel container configurations that were previously accepted but silently
executed sequentially, or failed only on first execution, now fail during
construction. These are invalid configurations rather than supported behavior.

## Validation

The complete `gear4jtest-core` production source set was compiled with
`javac --release 17` using minimal SLF4J API stubs. A standalone regression
harness executed the cancellation race, registry collision, conditional cleanup
and all three build-time validation cases successfully:

```text
phase3-smoke=OK
```

The three focused test classes changed or added for cancellation and registry
behavior were also compiled with minimal JUnit, AssertJ and Mockito API stubs.

Recommended repository validation commands:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*ParallelContainerBranchExecutorTest'
./gradlew :gear4jtest-core:test --tests '*ExecutionContextRegistryTest'
./gradlew :gear4jtest-core:test --tests '*AssemblyLineRunCleanupTest'
./gradlew :gear4jtest-core:test --tests '*StationBuilderTest'
./gradlew check
```

The Gradle distribution could not be downloaded in the implementation
environment because `services.gradle.org` was not resolvable, so these commands
must be executed in the user's network-enabled or already-cached workspace.

## Compilation follow-up

A Gradle compilation run exposed an AssertJ overload ambiguity on the new
race-regression assertion because `StationLogTrace#getOutput()` is generic.
The assertion now supplies the expected output type explicitly:

```java
assertThat(branchLog.<String>getOutput()).isEqualTo("input");
```

This follows the existing assertion pattern used by the core test suite and
prevents Java from considering AssertJ's `Predicate` and `IntPredicate`
overloads.
