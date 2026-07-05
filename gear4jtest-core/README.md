# gear4jtest-core

`gear4jtest-core` contains the Gear4J runtime engine and public Java API.

This module is the dependency-agnostic heart of the project. It must not depend on Spring, XML, Jackson-specific
behavior, external transport systems or storage-specific implementations. JDBC persistence lives in the optional
`gear4jtest-jdbc` module; core keeps only the persistence contracts and trace/record models needed by runtime SPIs.

## What this module owns

- `AssemblyLine`, `RunRequest`, `ExecutionResult` and explicit terminal outcomes.
- Station models and builders.
- Runtime execution through `AssemblyLineEngine`.
- Station strategies and runner chain construction.
- Flow decisions, stop/cancel semantics and error handling.
- Runtime events and side-compute hooks.
- Runtime traces and persistence abstractions.
- Extension SPI for run, station, executor and lifecycle behavior.
- Payload cloning SPI.
- AssemblyLine call execution in inline or nested-run mode.

## Package map

| Package           | Responsibility                                                                         |
|-------------------|----------------------------------------------------------------------------------------|
| `api`             | Public pipeline API: assembly lines, run requests, execution results and metadata.     |
| `api.behavior`    | User-facing operators, processors, conditions, skippers, signals and sibling outcomes. |
| `api.config`      | Flow, persistence, event and station configuration.                                    |
| `api.context`     | Execution context, execution services, station context and payload cloning SPI.        |
| `api.assemblyline`    | AssemblyLine references, assembly-line targets, nested-run context and runtime contracts.       |
| `api.station`     | Station model types.                                                                   |
| `api.util`        | Builder helpers.                                                                       |
| `engine`          | AssemblyLine engine, extension resolution and runtime orchestration.                       |
| `engine.runner`   | Runner-chain layers around station execution.                                          |
| `engine.strategy` | Station execution strategies.                                                          |
| `event`           | In-memory asynchronous event runtime and subscriptions.                                |
| `execution.trace` | Runtime trace objects for runs and stations.                                           |
| `persistence`     | Persistence status and repository abstractions.                                        |
| `sidecompute`     | Side-compute registry and listener integration.                                        |
| `spi`             | Extension, factory and runner SPIs.                                                    |

## Runtime execution model

A run starts from a fully built `AssemblyLine` and a `RunRequest`.

`AssemblyLineEngine` resolves default and request-level runtime extensions, creates the `EventManager`, merges default and
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
- a thread-confined pipeline call stack;
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
- `SignalStation`: emits explicit STOP or FATAL-like flow signals.
- `AssemblyLineCallStation`: calls another pipeline either inline or as a nested run.

Container branch ids are explicit and stable. They are used by sibling outcome conditions and named result aggregation,
so they are functional identifiers rather than cosmetic labels. Do not rely on station ids or random generated ids for
branches.

For containers, use named typed branches and `ContainerResults` aggregation instead of arity-specific builders or positional `Object...` aggregation:

```java
var price = Stations.branch("price", priceStation);
var stock = Stations.branch("stock", stockStation);

Stations.container("product-enrichment", Product.class)
        .withBranch(price)
        .withBranch(stock)
        .returns(results -> new ProductEnrichment(results.get(price), results.get(stock)));
```

The former one/two-branch arity-specific container wrappers have been removed before 1.0. The same generic model now
covers one, two or many branches.

A pipeline graph is expected to be fully configured before execution starts. Builders expose the mutation surface;
post-build flow and timeout setters are intentionally not public API.

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

Subscribed runs keep run-local queues, subscriptions and counters, while event dispatch work is multiplexed by a small
shared in-process dispatcher. Detached reactions that arrive after run cleanup may be skipped; waiters must keep
defensive timeouts.

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

The extension order matters. Lower order values run first; ties are resolved by implementation class name to keep
resolution deterministic. Keep extensions small and explicit. Prefer separate extensions over one large extension that
owns unrelated behavior. Extensions that require isolated nested-run execution or a specific inline runtime requirement
should expose that through `RuntimeExtension.requiresNestedRun()` or `requiredInlineRequirement()`.

## Worker concurrency

By default, worker instance reuse is guarded by the process-wide `WorkerConcurrencyManager.global()` when the
concurrency policy is `LOCK_PER_WORKER_INSTANCE`. This protects stateful singleton operators across independent engines
in the same JVM, but it can also serialize unrelated runs that share the same operator instance. Choose an engine-local
manager or an explicit parallel policy when that global safety trade-off is not desired.

`LOCK_REUSED_WORKER_INSTANCE_ONLY` is an opt-in optimization for applications whose `ResourceFactory` returns fresh
prototype/execution-scoped operators for non-reused stations. In that mode, Gear4J protects only workers explicitly
cached with `reuseOperatorInstanceWithinRun()`, avoiding registry churn for high-volume prototype operators. Do not use
it when a non-reused station can still receive the same non-thread-safe singleton from the `ResourceFactory`; keep the
default process-wide policy or the engine-local policy for that case.

A worker guard must be released by the same thread that acquired it. Current station strategies preserve that invariant;
future strategies that hand work off between threads must transfer or redesign the guard explicitly.

## Payload cloning

The core provides the `PayloadCloner` SPI and immutable-aware defaults.

Do not add Jackson-based payload cloning to this module. Use `gear4jtest-jackson` when mutable DTOs must be isolated
across branches. Storage-specific JSON codecs belong to their integration module, not core.

## AssemblyLine calls

`AssemblyLineCallStation` supports two execution modes:

- `INLINE`: executes a child pipeline inside the current run boundary when its runtime requirements are compatible.
- `NESTED_RUN`: creates a nested run with its own execution trace and runtime setup. User context propagation is
  controlled by `ContextPropagationPolicy`; the default is a shallow copy of all parent context values. Configure the
  engine with `ContextPropagationPolicy.none()`, `includeKeys(...)` or `copyValues(...)` when nested-run context
  isolation matters.

A running pipeline graph must remain stable for the duration of the run. Inline pipeline recursion detection is
thread-confined and propagated to parallel branch tasks so sibling branches do not contaminate each other's call stack.

## JDBC persistence support

JDBC execution persistence is intentionally outside core. Use the optional `gear4jtest-jdbc` module for
`DatabaseExecutionManager`, `DatabaseAssemblyRunRepository`, `Gear4jDatabaseDialect` and schema migrations.

The built-in `PersistenceExtension` persists station start snapshots immediately
and batches terminal station snapshots per run with `appendAll(...)` before
calling `end(run)`. Build it with
`PersistenceExtension.builder(manager).terminalRecordBatchSize(1)` when an
application prefers one terminal flush per station over batched completion
persistence.

Core applications can still depend only on the generic contracts:

- `AssemblyRunManager`;
- `AssemblyRunRepository`;
- `AssemblyRunRecord`;
- `StationLogRecord`;
- `PersistenceRuntimeMonitor` / `PersistenceRuntimeStats` for implementation-neutral observability.

## Testing

Use JUnit 5 and AssertJ. Prefer tests that cover actual runtime behavior, not only object construction.

Useful focused tasks:

```bash
./gradlew :gear4jtest-core:test
./gradlew :gear4jtest-core:test --tests '*EventManagerTest'
./gradlew :gear4jtest-core:test --tests '*AssemblyLineCallStationStrategyTest'
./gradlew :gear4jtest-core:test --tests '*ContainerStationStrategyTest'
./gradlew :gear4jtest-core:integrationTest
```

Unit tests live under `src/test/java`. Integration tests live under `src/integrationTest/java` and run through the
`integrationTest` task. Tests that need databases use Testcontainers, so the container lifecycle is owned by JUnit
tests themselves.

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
