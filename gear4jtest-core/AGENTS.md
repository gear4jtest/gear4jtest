# gear4jtest-core agent notes

This module owns the runtime engine and public Java API.

## Do not

- Do not add Spring, XML, Jackson-specific, external transport or storage-specific dependencies to this module.
- Do not make flow decisions from station logs, persistence records or database snapshots.
- Do not turn `EventManager` into a durable broker abstraction.
- Do not catch JVM `Error` as an ordinary recoverable pipeline failure.
- Do not shut down caller-provided executors unless ownership is explicit.
- Do not expose internal engine services to user operators unless the API intentionally does so.

## When changing execution behavior

Review the impact on:

- `PipelineEngine`
- `RunnerChainFactory`
- `StationLifecycleRunner`
- `StationExceptionBoundaryRunner`
- `StationErrorPolicyExecutor`
- `ContainerStationStrategy`
- `PipelineCallStationStrategy`
- `FlowDecider`
- `ExecutionContext`
- `ExecutionServices`

## Runtime invariants

- `ExecutionContext` is run state.
- `ExecutionServices` is run-scoped service access.
- Station logs are observability data.
- STOP/CANCEL semantics are handled as station outcomes and flow decisions.
- Event reactions are best-effort.
- Payload cloning goes through `PayloadCloner`.
- Pipeline calls must guard against recursive call-stack problems.

## Formatting

Repository-wide formatting instructions from the root `AGENTS.md` apply here. Do not manually mimic the Java style; use
the Gradle formatter and respect Checkstyle failures.

Before finishing code changes in this module, run when possible:

```bash
./gradlew spotlessApply
./gradlew check
```

## Test focus

Useful focused tests:

```bash
./gradlew :gear4jtest-core:test --tests '*ContainerStationStrategyTest'
./gradlew :gear4jtest-core:test --tests '*PipelineCallStationStrategyTest'
./gradlew :gear4jtest-core:test --tests '*EventManagerTest'
./gradlew :gear4jtest-core:test --tests '*ExecutionContextTest'
```
