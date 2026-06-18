# gear4jtest-core

`gear4jtest-core` contains the Gear4J runtime engine and public Java API.

This module is the dependency-agnostic heart of the project. It should not depend on Spring, XML, Jackson-specific
behavior, external transport systems or storage-specific assumptions.

## What this module owns

- `AssemblyLine`, `RunRequest`, `ExecutionResult` and explicit terminal outcomes.
- Station models and builders.
- Runtime execution through `PipelineEngine`.
- Station strategies and runner chain construction.
- Flow decisions, stop/cancel semantics and error handling.
- Runtime events and side-compute hooks.
- Runtime traces and persistence abstractions.
- Extension SPI for run, station, executor and lifecycle behavior.
- Payload cloning SPI.
- Pipeline call execution in inline or nested-run mode.

## Package map

| Package           | Responsibility                                                                         |
|-------------------|----------------------------------------------------------------------------------------|
| `api`             | Public pipeline API: assembly lines, run requests, execution results and metadata.     |
| `api.behavior`    | User-facing operators, processors, conditions, skippers, signals and sibling outcomes. |
| `api.config`      | Flow, persistence, event and station configuration.                                    |
| `api.context`     | Execution context, execution services, station context and payload cloning SPI.        |
| `api.pipeline`    | Pipeline references, pipeline targets, nested-run context and runtime contracts.       |
| `api.station`     | Station model types.                                                                   |
| `api.util`        | Builder helpers.                                                                       |
| `engine`          | Pipeline engine, extension resolution and runtime orchestration.                       |
| `engine.runner`   | Runner-chain layers around station execution.                                          |
| `engine.strategy` | Station execution strategies.                                                          |
| `event`           | In-memory asynchronous event runtime and subscriptions.                                |
| `execution.trace` | Runtime trace objects for runs and stations.                                           |
| `persistence`     | Persistence status and repository abstractions.                                        |
| `sidecompute`     | Side-compute registry and listener integration.                                        |
| `spi`             | Extension, factory and runner SPIs.                                                    |

## Runtime execution model

A run starts from a fully built `AssemblyLine` and a `RunRequest`.

`PipelineEngine` resolves default and request-level runtime extensions, creates the `EventManager`, merges default and
request context, registers an `ExecutionContext`, builds the root station runner, then executes the root station through
the runner chain.

The default runner chain separates concerns:

- lifecycle and scope initialization;
- persistence-aware observation;
- station exception boundary handling;
- terminal strategy execution.

Flow decisions must be driven by runtime state and station outcomes, not by persisted records.

## Context model

`ExecutionContext` owns mutable run state:

- execution id;
- pipeline id;
- user context map;
- runtime contract;
- call stack;
- runtime trace;
- event runtime options;
- a cooperative `CancellationToken` for long-running user operators.

`ExecutionServices` owns run-scoped services:

- `EventManager`;
- `ResourceFactory`;
- `StationScopedResourceRegistry`.

Keep this separation. User operators should get only the station execution context and the services intentionally
exposed through it. Internal engine services should not be leaked as general user dependencies.

## Station model

Main station kinds:

- `WorkStation`: invokes a user operator.
- `UnaryWorkStation`: work station whose input and output type are the same.
- `SequenceStation`: executes children in order.
- `ContainerBaseStation`: executes multiple branches, sequentially or with an executor.
- `UnaryIfElseContainerStation`: conditional branch container.
- `IteratorStation`: iterates over input and accumulates results.
- `SignalStation`: emits STOP, CANCEL or FATAL-like flow signals.
- `PipelineCallStation`: calls another pipeline either inline or as a nested run.

Container branch ids are explicit and stable. They are used by sibling outcome conditions, so they are functional
identifiers rather than cosmetic labels. Do not rely on station ids or random generated ids for branches.

## Flow and error semantics

Important principles:

- `FlowConfig` and `FlowDecider` decide how station outcomes affect the rest of the run.
- STOP and CANCEL are flow outcomes, not generic exceptions used for short-circuiting.
- `ExecutionResult.isSuccess()` is true only for `SUCCEEDED`; use `getOutcome()` to distinguish `SKIPPED`, `STOPPED`, `CANCELLED` and `FAILED`.
- JVM `Error` should not be treated as an ordinary recoverable pipeline failure.
- Station-level error policies belong at the station boundary, not scattered through every strategy.
- Persistence traces and logs are observability artifacts, not control-flow inputs.

## Events

The core event runtime is intentionally in-memory and best-effort.

It is suitable for:

- local asynchronous reactions;
- side-compute;
- observability callbacks;
- non-critical enrichment.

It does not provide:

- durable delivery;
- replay;
- exactly-once semantics;
- crash recovery;
- guaranteed external publication.

For durable integration with Kafka, SQS, RabbitMQ or an outbox, use a separate future module or subsystem instead of
turning `EventManager` into a broker abstraction.

## Extensions

`RuntimeExtension` is the base SPI. Dedicated subtypes keep each concern narrow:

- `RunInterceptorExtension`: wraps the whole run with around-run behavior.
- `RunLifecycleExtension`: observes run start/end lifecycle.
- `StationWrapperExtension`: decorates station runners.
- `StationLifecycleExtension`: observes station lifecycle.
- `ExecutorWrapperExtension`: decorates executors used by asynchronous work.

The extension order matters. Keep extensions small and explicit. Prefer separate extensions over one large extension
that owns unrelated behavior.

## Payload cloning

The core provides the `PayloadCloner` SPI and immutable-aware defaults.

Do not hard-code Jackson into this module. Use `gear4jtest-jackson` when mutable DTOs must be isolated across branches.

## Pipeline calls

`PipelineCallStation` supports two execution modes:

- `INLINE`: executes a child pipeline inside the current run boundary when its runtime requirements are compatible.
- `NESTED_RUN`: creates a nested run with its own execution trace and runtime setup while inheriting selected parent
  context for the current MVP.

A running pipeline graph must remain stable for the duration of the run.

## JDBC persistence support

The built-in JDBC persistence is intentionally provider-scoped rather than advertised as generic JDBC support.

Supported for the MVP:

- PostgreSQL;
- MySQL 8;
- MariaDB through the MySQL-compatible script and dialect path;
- Oracle through its dedicated schema and JDBC dialect path;
- H2 for tests and local development.

Applications must explicitly supply a `Gear4jDatabaseDialect` when constructing JDBC persistence components. Gear4J
does not infer the dialect from `DatabaseMetaData`: dialect selection controls migration resources, SQL syntax and
JDBC bindings, so configuration must be deterministic before a run starts.

For example:

```java
var persistenceRuntime = PersistenceRuntimeConfiguration.builder()
        .batchSize(500)
        .maxPendingLogsPerRun(10_000)
        .flushInterval(Duration.ofSeconds(1))
        .build();
DatabaseExecutionManager.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .configuration(persistenceRuntime)
        .autoCreateTables(true)
        .build();
DatabaseAssemblyRunRepository.builder().dataSource(dataSource).databaseDialect(Gear4jDatabaseDialect.POSTGRESQL).build();
```

`DatabaseExecutionManager` keeps station-log buffers bounded, periodically flushes low-volume runs and exposes
`PersistenceRuntimeStats`. If an application supplies executors to it, those executors remain owned by the application.
Use `SensitiveDataRedactor` for persisted values that can contain secrets or personal information.

Extending support for a new provider should add a `Gear4jDatabaseDialect` entry, versioned migration resources, and integration tests
for run and station-log persistence.

## Testing

Use JUnit 5 and AssertJ. Prefer tests that cover actual runtime behavior, not only object construction.

Useful focused tasks:

```bash
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-core:test --tests '*EventManagerTest'
./gradlew :gear4jtest-core:test --tests '*PipelineCallStationStrategyTest'
./gradlew :gear4jtest-core:test --tests '*ContainerStationStrategyTest'
./gradlew :gear4jtest-core:integrationTest
```

Unit tests live under `src/test/java`. Integration tests live under `src/integrationTest/java` and run through the
`integrationTest` task. Tests that need databases use Testcontainers, so the container lifecycle is owned by JUnit
tests themselves.

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
