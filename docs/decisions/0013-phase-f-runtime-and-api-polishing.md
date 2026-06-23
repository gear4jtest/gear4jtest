# Phase F runtime and API polishing

## Status

Accepted.

## Context

The post-audit cleanup identified several remaining topics after the initial
safe phases: event dispatcher scalability, persistence batching, nested context
copying, side-compute behavior, taxonomy drift, the historical
builder helper facades, builder style conventions, typed
containers beyond two branches, JPMS and minor code-quality debt.

## Decisions

### Event dispatcher

Implement the event runtime improvement now: per-run `EventManager` instances no
longer create a dedicated dispatcher thread. Dispatch tasks are multiplexed by a
small shared in-process dispatcher. Run-local subscriptions, counters, queue
capacity accounting and shutdown semantics remain owned by the per-run manager.

This keeps the event runtime lightweight and best-effort. It is not a durable
broker, does not add replay and does not define global ordering across runs.

### Persistence batching

Persistence batching was intentionally deferred from Phase F and implemented in
ADR 0015. Station start snapshots remain immediate, while terminal station
snapshots are batched per run and flushed before the run record is ended.

### Nested context propagation

Implemented in ADR 0016. Nested runs now use an explicit
`ContextPropagationPolicy`. The default remains a shallow map copy for backward
compatibility, while applications can disable propagation, allow-list keys or
copy mutable values deliberately.

### Side-compute

Keep the current side-compute model and document it: station execution waits
synchronously for a result produced through asynchronous event/reaction
infrastructure. If side-compute becomes high-volume or latency-sensitive, revisit
executor isolation, timeout defaults and metrics.

### Taxonomies

Reduce drift without keeping redundant public enums. `ExecutionOutcome` now owns
the mapping to/from terminal `ExecutionStatus`, and `StationLogStatus` exposes
its corresponding `ExecutionStatus`. `SignalStation` uses the shared
`SignalType` enum directly and accepts only `STOP` and `FATAL`; `IGNORE` remains
meaningful for error policies, not explicit flow-signal stations. The old
station-specific `StationSignalType` adapter was removed before 1.0.

### Builder helper facades

Remove the historical `ElementModelBuilders` umbrella before 1.0 instead of
keeping a compatibility layer. The focused facades are now the only builder
helper surface:

- `AssemblyLines`;
- `Stations`;
- `Errors`;
- `Events`;
- `Persistence`;
- `Concurrency`;
- `RuntimeContracts`.

### Builder / record / wither style

Use records plus `with...` methods for small immutable value objects with flat
configuration. Use builders when construction needs generic type narrowing,
progressive station-chain construction, multiple optional families or validation
that would make constructors unreadable. Do not migrate existing public builders
mechanically; apply the convention to new APIs and opportunistic refactors.

### Containers beyond two typed branches

Do not add `Container3Station`, `Container4Station`, etc. mechanically. The
follow-up named-results model is captured in
`0014-named-typed-container-results.md`: container code should use
`ContainerBranch<IN, OUT>` handles and `ContainerResults` aggregation for one,
two or many branches. The former one/two-branch wrappers were removed before
1.0.

### JPMS

Keep JPMS as a pre-1.0 note. Source-level markers and architecture tests remain
the boundary mechanism for now.

### Minor code debt

Rename tests whose names were driven by coverage tooling rather than behavior.
Clean up `BaseError` builder duplication without changing the public nested
classes.
