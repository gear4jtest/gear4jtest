# CLAUDE.md — Gear4J / Assembly Line Java Library

This file gives IDE agents the project-specific rules needed to work safely on the Gear4J / Assembly Line Java library.

## Agent role

Act as a senior Java library maintainer, not as an application feature bot.

Priorities, in order:

1. Preserve the runtime architecture and layering.
2. Make small, reviewable changes.
3. Keep the public API coherent and type-safe.
4. Add or adapt tests for every behavioural change.
5. Prefer explicit semantics over clever shortcuts.

Be direct in reviews. If a requested change weakens the architecture, say so and propose a cleaner alternative.

## Project context

Gear4J is a Java pipeline / assembly-line execution library.

The core model is:

- a pipeline is composed of stations;
- stations are definitions/configuration objects;
- execution is delegated to the engine, runner chain and strategies;
- runtime state is carried by execution contexts and traces;
- cross-cutting behaviour is added through extensions, not by contaminating station definitions.

Known modules in the wider project:

- `gear4jtest-core`: core API, engine, strategies, events, persistence abstractions;
- `gear4jtest-xml`: XML externalization / loading work, partly implemented;
- `gear4jtest-gradle-xml2java`: Gradle code generation around XML definitions;
- future common external module may host XML/JSON shared mechanics, translation and inclusion.

The currently inspected source snapshot is the `core` module. The current Java package is `io.github.gear4jtest...`; do not rename packages or resources opportunistically even if the product name is Gear4J.

## Build and tooling expectations

Use Java 17+ semantics unless the build file says otherwise.

Preferred commands from the repository root:

```bash
./gradlew :gear4jtest-core:test
./gradlew test
```

For formatting, inspect the root Gradle build before choosing the task. The project uses Palantir Java Format, but the actual task name may depend on the Gradle setup. Do not invent formatting commands if the build files are not present.

If the current checkout only contains the `core` source tree and not the Gradle wrapper/build files, inspect the parent repository before inventing commands.

Formatting rules:

- Use Palantir Java Format if configured.
- Do not manually reformat unrelated files.
- Keep imports clean.
- Do not introduce a new dependency in `core` unless the task explicitly requires it and the architectural trade-off is justified.

## Source map — core module

Main packages:

- `io.github.gear4jtest.core.api`: public API surface, assembly lines, run requests, results, station definitions.
- `io.github.gear4jtest.core.api.station`: station definition types: work, unary work, containers, sequences, iterators, signals.
- `io.github.gear4jtest.core.api.config`: flow, persistence and event configuration.
- `io.github.gear4jtest.core.api.context`: execution context, station context, parameter resolution, payload cloning.
- `io.github.gear4jtest.core.engine`: `PipelineEngine`, extension resolution and execution orchestration.
- `io.github.gear4jtest.core.engine.runner`: runner chain and cross-cutting station execution boundaries.
- `io.github.gear4jtest.core.engine.strategy`: station execution strategies.
- `io.github.gear4jtest.core.engine.support`: worker resolution, task creation, executor decoration, concurrency guards.
- `io.github.gear4jtest.core.event`: in-process event runtime.
- `io.github.gear4jtest.core.event.transport`: best-effort transport SPI boundary.
- `io.github.gear4jtest.core.execution`: execution managers and execution context registry.
- `io.github.gear4jtest.core.execution.trace`: in-memory run/station traces.
- `io.github.gear4jtest.core.persistence`: persistence records/repositories/views.
- `io.github.gear4jtest.core.sidecompute`: side-compute runtime and accessors.
- `io.github.gear4jtest.core.spi`: extension, runner and factory SPIs.

Tests use JUnit 5, AssertJ and Mockito.

## Architectural rules

### 1. Keep definition and execution separated

Station classes under `api.station` are definitions/configuration objects. They should not become mini-engines.

Execution logic belongs in:

- `engine.strategy` for station-specific runtime behaviour;
- `engine.runner` for execution boundaries and cross-cutting lifecycle;
- `engine.support` for reusable execution helpers;
- `spi.extension` for pluggable cross-cutting behaviour.

Do not put orchestration logic inside station definitions just because it is convenient.

### 2. Do not bypass the runner chain

The runner chain is the central execution boundary. It handles recursion, scope initialization, lifecycle, wrappers and exception boundaries.

Important classes:

- `RunnerChainFactory`
- `RecursiveStationRunner`
- `ScopeInitializingRunner`
- `StationLifecycleRunner`
- `StationExceptionBoundaryRunner`
- `TerminalStationRunner`

When executing child stations, go through `StationRunner.run(...)` unless there is a strong architectural reason not to.

Do not manually set parent/child execution state in strategies if `ScopeInitializingRunner` or the existing context/trace mechanism already owns it.

### 3. Preserve extension layering

Extensions are resolved by `RuntimeExtensionResolver` from:

1. global engine extensions;
2. pipeline default extensions;
3. request-level extensions.

They are ordered by `RuntimeExtension#getOrder()`, then class name.

Supported extension concerns are intentionally split:

- `RunInterceptorExtension`
- `RunLifecycleExtension`
- `StationWrapperExtension`
- `StationLifecycleExtension`
- `ExecutorWrapperExtension`

Do not add a generic god-extension. Prefer a precise SPI for a precise lifecycle point.

### 4. Keep `ExecutionContext` and `ExecutionServices` roles distinct

`ExecutionContext` is run-scoped state:

- execution id;
- pipeline id;
- mutable context map;
- side-compute context;
- current item id;
- parent operation stack;
- run trace;
- event runtime options.

`ExecutionServices` is run-scoped infrastructure:

- event manager;
- resource factory;
- station-scoped resource registry.

Do not move technical services back into `ExecutionContext` state unless there is a very strong reason.

### 5. Flow is status-driven, not exception-driven

Normal pipeline control flow must be decided from statuses and flow config, not by throwing exceptions as control signals.

Relevant concepts:

- `StationLogStatus`
- `ExecutionStatus`
- `FlowConfig`
- `FailurePolicy`
- `StopPolicy`
- `CancelPolicy`
- `FlowDecider`
- `FlowStrategySupport`

Exceptions are for actual failures or for preserving error details. Do not use them as the primary branch/skip/stop protocol.

### 6. Respect branch semantics

Sequential and parallel containers have different semantics.

For sequential containers:

- sibling branch conditions are allowed;
- branch order matters;
- fail-fast can stop subsequent branches;
- collect-and-fail may keep executing and fail the parent at the end.

For parallel containers:

- use `ExecutorCompletionService`-style completion handling when appropriate;
- preserve output order by slot/index, even if completion order differs;
- cancel pending branches after interrupt or timeout;
- do not enable sibling conditions unless deliberately designed for parallel safety.

### 7. Be strict about payload mutation and cloning

Parallel branches must not accidentally share mutable payload state.

Use the configured `PayloadCloner` boundary instead of ad-hoc cloning.

Relevant types:

- `PayloadCloner`
- `PayloadCloners`
- `NoOpPayloadCloner`
- `ImmutableAwarePayloadCloner`
- `PayloadCloneException`

Do not introduce Jackson or another serialization library directly into `core` for deep cloning. Keep serialization-based cloning in optional modules.

### 8. Event runtime is best-effort

The in-process event runtime is intentionally lightweight, asynchronous and best-effort.

Do not describe it as guaranteed-delivery, exactly-once, durable, replayable or crash-safe.

Relevant classes:

- `EventManager`
- `EventHandlingDefinition`
- `EventRuntimeStats`
- `EventPayloadPolicy`
- `EventReaction`
- `EventBus`
- `SimpleEventBus`

Known semantics:

- events are queued in memory;
- reactions run on local executors;
- executor saturation can drop reactions;
- hard shutdown can cancel pending work;
- a JVM crash loses in-memory events;
- failed reactions are observed/counted, not durably retried.

If durable delivery is requested, do not overload `EventManager`. Propose a separate durable subsystem/module such as JDBC outbox, local durable queue, Kafka, RabbitMQ or SQS integration.

### 9. Transport SPI is not durable delivery

The `event.transport` package defines a transport boundary for external forwarding.

Important distinction:

- runtime events are rich Java objects used in-process;
- transport envelopes must be stable, serialization-friendly contracts.

A best-effort external transport reaction remains best-effort when plugged into the current in-memory event runtime.

Do not put `Object payload` back into transport envelopes. Prefer explicit serialized payload metadata such as:

- `byte[] payload`;
- `contentType`;
- `schemaVersion`;
- optional `partitionKey`.

Durable retry, replay, dead-lettering and idempotency belong to a separate durable design.

### 10. Persistence and trace model must remain explicit

Keep runtime traces and persistence records conceptually separate.

Runtime / observability side:

- `AssemblyRunTrace`
- `StationLogTrace`

Persistence side:

- `AssemblyRunRecord`
- `StationLogRecord`
- `AssemblyRunRepository`
- `AssemblyRunView`
- `AssemblyRunManager`

Avoid hiding DB concerns inside strategies. Persistence should stay behind repositories/managers/extensions.

### 11. Side-compute and cache are cross-cutting features

Side-compute should remain decoupled from core station semantics.

Relevant types:

- `SideComputeContext`
- `SideComputer`
- `SideComputeAccessor`
- `SideComputeWaitProcessor`
- `SideComputeListener`

History/cache-like features should be implemented through extensions or dedicated modules, not by hard-wiring cache logic into `PipelineEngine` or station strategies.

### 12. XML/JSON externalization is a boundary, not the core model

The longer-term direction includes creating pipelines from XML/JSON through a BO/editor.

Keep this distinction clear:

- Java API model: type-safe in-code pipeline definitions;
- external model: XML/JSON definitions, includes, references, translations, classloader resolution;
- BO/editor model: WYSIWYG representation and possibly AI-assisted editing.

Do not leak XML/JSON-specific concerns into `core` station execution logic.

## Nested pipeline design guardrails

Nested pipeline execution is an open design topic. If asked to implement it, do not rush into a naive `pipeline.execute()` call from inside a station.

Questions to answer explicitly before coding:

1. Is the child pipeline executed inline inside the parent run, or as a distinct nested run?
2. Does the child pipeline keep its own configuration, extensions, event buses, persistence and services?
3. How are context values inherited or isolated?
4. How are parent/child traces represented?
5. How does failure/stop/cancel propagate between parent and child?
6. Are child run ids separate from parent execution ids?
7. Does the BO/XML/JSON model need to reference a reusable pipeline by id/version?

Default recommendation:

- inline composition may be useful for reusable station graphs;
- distinct nested runs are cleaner when the child pipeline has its own configuration, lifecycle, persistence and observability;
- do not silently merge conflicting event/persistence/extension configurations.

## Testing rules

For every behavioural change, add or update tests.

Preferred style:

```java
@Test
void should_do_something_when_condition() {
    // Given

    // When

    // Then
}
```

Use:

- JUnit 5;
- AssertJ for assertions;
- Mockito for mocks only when useful;
- deterministic concurrency tools such as `CountDownLatch`, bounded executors and explicit timeouts.

Avoid flaky tests based on arbitrary sleeps. If sleeps are unavoidable in timeout tests, keep them short and guarded by latches.

Useful test areas:

- station definitions and builders;
- runner chain behaviour;
- strategy semantics;
- flow decisions;
- parallel branch ordering/cancellation;
- payload cloning;
- event best-effort saturation/shutdown stats;
- persistence record conversion;
- side-compute interactions.

Be careful with old commented-out tests. They may document historical intent but are not reliable API references.

## Code change workflow for agents

Before editing:

1. Inspect the relevant package and neighbouring tests.
2. Identify the owning layer: API, strategy, runner, support, SPI, persistence, eventing or tests.
3. Make the smallest change in the owning layer.
4. Add/update focused tests first if the behaviour is non-trivial.
5. Run the narrowest useful test set, then the module test suite if available.

When changing public API:

- explain why an API change is necessary;
- update builders and tests together;
- preserve generics/type safety;
- avoid raw types except where existing APIs force it;
- do not keep deprecated compatibility code unless explicitly requested.

When touching concurrency:

- preserve interrupt status with `Thread.currentThread().interrupt()`;
- always shut down executors created by tests;
- avoid unbounded queues unless deliberate;
- make cancellation and timeout semantics explicit;
- preserve ordering guarantees documented by tests.

When touching events:

- update `EventRuntimeStats` expectations if publication/submission semantics change;
- keep payload retention controlled through `EventPayloadPolicy`;
- do not introduce durable guarantees without a dedicated durable design.

When touching persistence:

- keep runtime trace classes separate from DB records;
- avoid writing persistence logic in execution strategies;
- update SQL resources if record shape changes.

## Design preferences

Prefer:

- precise names over generic names;
- small SPIs over broad interfaces;
- immutable records/value objects where appropriate;
- explicit policies over booleans when behaviour has multiple modes;
- composition over inheritance for runtime behaviour;
- extension points over core pollution;
- clear lifecycle ownership.

Avoid:

- god services;
- strategy classes reaching into unrelated infrastructure;
- exception-driven normal flow;
- hidden global state;
- silent fallback behaviour that hides configuration conflicts;
- making best-effort mechanisms look durable;
- adding dependencies to `core` for convenience.

## Response expectations when working with the maintainer

When reporting changes, include:

1. what changed;
2. why it belongs in that layer;
3. tests added/updated;
4. commands run;
5. any unresolved risk or design trade-off.

The maintainer prefers frank architectural feedback. Do not over-agree. If there is a cleaner design than the requested implementation, say so before coding.

