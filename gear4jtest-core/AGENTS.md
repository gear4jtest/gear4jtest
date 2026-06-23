# gear4jtest-core agent notes

This module owns the runtime engine and public Java API.

## Do not

- Do not add Spring, XML, new Jackson-specific behavior, external transport or new storage-specific dependencies to this module.
  The current in-core JDBC persistence and its Jackson JSON codec are a pre-1.0 legacy exception; keep them isolated and do not broaden that surface.
- Do not make flow decisions from station logs, persistence records or database snapshots.
- Do not turn `EventManager` into a durable broker abstraction.
- Do not catch JVM `Error` as an ordinary recoverable pipeline failure.
- Do not shut down caller-provided executors unless ownership is explicit.
- Do not expose internal engine services to user operators unless the API intentionally does so.

## When changing execution behavior

Review the impact on:

- `AssemblyLineEngine`
- `RunnerChainFactory`
- `StationLifecycleRunner`
- `StationExceptionBoundaryRunner`
- `StationErrorPolicyExecutor`
- `ContainerStationStrategy`
- `AssemblyLineCallStationStrategy`
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
- A running pipeline graph must remain stable for the duration of a run.
- AssemblyLine calls must guard against recursive call-stack problems, including parallel branch execution.

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
./gradlew :gear4jtest-core:test --tests '*AssemblyLineCallStationStrategyTest'
./gradlew :gear4jtest-core:test --tests '*EventManagerTest'
./gradlew :gear4jtest-core:test --tests '*ExecutionContextTest'
```
