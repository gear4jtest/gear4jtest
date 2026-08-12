# gear4jtest-micrometer

Optional Micrometer instrumentation for Gear4J runtime lifecycle events.

The core runtime has no Micrometer dependency. Add this module only when you want
metrics and when your application already exposes a Micrometer `MeterRegistry`.

## Document control

| Field | Value |
| --- | --- |
| Status | Critical operational surface implemented; optional additions tracked below |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-08-13 |
| Architecture reference | [Micrometer observability](../docs/architecture/micrometer-observability.md) |

The module covers lifecycle, event/persistence pressure and the generated-code
infrastructure involved in artifact reads, translation, compilation,
classloading and injection. Advanced business metrics and application-specific
SLOs remain the application's responsibility.

## Current metrics

The metric surface focuses on low-cardinality runtime signals:

```text
gear4j.runs.started
gear4j.runs.completed
gear4j.runs.duration
gear4j.stations.started
gear4j.stations.completed
gear4j.stations.duration
gear4j.branches.started
gear4j.branches.completed
gear4j.branches.duration
gear4j.branches.rejected
gear4j.persistence.buffered.station.logs
gear4j.persistence.buffered.station.logs.oldest.age.seconds
gear4j.persistence.active.runs
gear4j.persistence.flushes.scheduled
gear4j.persistence.flushes.completed
gear4j.persistence.flushes.failed
gear4j.persistence.appends.rejected
gear4j.persistence.flush.duration{trigger,outcome}
gear4j.events.published
gear4j.events.dispatched
gear4j.events.dropped
gear4j.events.queued
gear4j.events.queue.remaining.capacity
gear4j.reactions.submitted
gear4j.reactions.completed
gear4j.reactions.dropped
gear4j.reactions.failed
gear4j.reactions.pending
gear4j.reactions.in.flight
gear4j.events.process.active.runtimes
gear4j.events.process.queued
gear4j.events.process.dropped
gear4j.reactions.process.dropped
gear4j.events.process.dispatcher.rejected
gear4j.events.process.dispatch.latency.average.nanos
gear4j.events.process.dispatch.latency.max.nanos

gear4j.generated.loading.cache.requests{result}
gear4j.generated.loading.loads{outcome}
gear4j.generated.loading.duration
gear4j.generated.loading.duration.max.nanos
gear4j.generated.loading.phase.duration{phase}
gear4j.generated.loading.phase.duration.max.nanos{phase}
gear4j.generated.loading.phase.failures{phase}
gear4j.generated.loading.artifact.integrity.failures
gear4j.generated.loading.in.flight
gear4j.generated.loading.executor.active
gear4j.generated.loading.executor.queued
gear4j.generated.loading.shutdown

gear4j.generated.compilation.cache.requests{result}
gear4j.generated.compilations{outcome}
gear4j.generated.compilation.duration
gear4j.generated.compilation.duration.max.nanos
gear4j.generated.compilation.cache.entries
gear4j.generated.compilation.cache.bytes
gear4j.generated.compilation.in.flight
gear4j.generated.compilation.executor.active
gear4j.generated.compilation.executor.queued
gear4j.generated.compilation.shutdown

gear4j.generated.classloaders.cached
gear4j.generated.classloaders.capacity
gear4j.generated.classloaders.protected
gear4j.generated.classloaders.protected.capacity
gear4j.generated.classloaders.aliases
gear4j.generated.classloaders.bytecode.bytes
gear4j.generated.classloaders.bytecode.capacity.bytes
gear4j.generated.classloaders.protected.over.capacity
gear4j.generated.classloaders.evictions
gear4j.generated.classloaders.rejections

gear4j.artifacts.store.operations{operation,outcome}
gear4j.artifacts.store.bytes{operation}
gear4j.artifacts.store.operation.duration{operation}
gear4j.artifacts.store.cleanup.failures
gear4j.artifacts.spool.files
gear4j.artifacts.spool.bytes
gear4j.artifacts.spool.capacity.bytes
gear4j.artifacts.spool.stale.files.deleted
gear4j.artifacts.spool.stale.bytes.deleted
gear4j.artifacts.spool.quota.rejections
gear4j.artifacts.spool.cleanup.failures
```

These metrics are useful to confirm that Gear4J is active, track completed run,
station and branch latency, and ensure that persistence and the best-effort event
runtime are not silently accumulating, saturating, running late reactions or dropping work. They are still
intentionally conservative.

Branch `started` counts only work that entered the ordinary station runner.
Branch `completed{status}` also includes synthetic skip, cancellation,
interruption and failed-before-start outcomes; `rejected` is the subset rejected
by the branch executor. `duration{status}` is recorded only for an executed
branch with consistent timestamps.

The flush timer measures real attempts only. `trigger` is one of `async`,
`explicit`, `terminal` or `shutdown`; `outcome` is one of `succeeded`, `failed`,
`rejected`, `timed_out` or `interrupted`. Async duration starts at admission and
includes executor queue delay. Empty explicit/terminal calls do not create a
sample. Percentile histograms are enabled so a compatible backend can calculate
p95/p99 distributions.

`PersistenceMetricsBinder` is auto-registered by the Spring Boot starter when a
`DatabaseExecutionManager` and a `MeterRegistry` are available. The starter also calls
`EventMetricsBinder.bindProcessWide(...)` once for the tag-free aggregate. Run-local binding remains internal because
the concrete event-runtime lifecycle is not part of the stable consumer API.

Manual integrations may retain and close the removable observation subscription:

```java
PersistenceFlushSubscription subscription =
        PersistenceMetricsBinder.bindWithSubscription(registry, persistenceMonitor);
```

The starter also auto-binds `AssemblyLineManager`,
`InMemoryClassLoaderRegistry`, `ArtifactStoreMonitor` and
`ArtifactSpoolMonitor` when exactly one bean of the corresponding type exists.
Multiple stores or managers are intentionally not guessed: bind them manually
to separate registries or application-defined bounded dimensions.

```java
GeneratedLoadingMetricsBinder.bind(registry, assemblyLineManager);
ClassLoaderMetricsBinder.bind(registry, classLoaderRegistry);
ArtifactStoreMetricsBinder.bind(registry, artifactStoreMonitor);
ArtifactSpoolMetricsBinder.bind(registry, artifactSpoolMonitor);
```

## Why the current surface is conservative

Metrics with tags such as `assemblyLineId`, `operationId`, `exceptionClass` or
`stationId` can quickly create high-cardinality time series, especially if
pipelines are generated dynamically by a BO.

Infrastructure binders never expose application identifiers. Their only tags
are the closed sets `phase`, `outcome`, `result`, `operation` and `trigger`; no exception
class, exception message, hash, pipeline ID or business content is emitted.

## Delivered and remaining application-specific observability

| Surface | Status | Existing signal or remaining gap | Target version | Last verified |
| --- | --- | --- | --- | --- |
| Run outcomes and duration | `DELIVERED` | `gear4j.runs.completed{status}` and `gear4j.runs.duration{status}` | 1.0 | 2026-08-13 |
| Station outcomes and duration | `DELIVERED` | `gear4j.stations.completed{status}` and `gear4j.stations.duration{status}` | 1.0 | 2026-08-13 |
| Event-reaction outcomes | `DELIVERED` | Separate completed, failed and dropped counters avoid an additional tagged duplicate meter | 1.0 | 2026-08-13 |
| Pipeline, operation or branch identifiers | `APPLICATION` | Use an explicit bounded `Gear4jMeterTagPolicy`; raw dynamic identifiers are not default metrics | Application-owned | 2026-08-13 |
| Application timeout categorization | `BACKLOG` | Run/station failure causes require a stable low-cardinality error-classification contract | Post-1.0; unscheduled | 2026-08-13 |
| Persistence flush duration distribution | `DELIVERED` | `gear4j.persistence.flush.duration{trigger,outcome}` enables bounded backend percentiles | 1.0 | 2026-08-13 |
| Parallel-branch rejections and duration | `DELIVERED` | Branch lifecycle meters cover executed and synthetic terminal outcomes without engine coupling | 1.0 | 2026-08-13 |
| Experimental-cache outcomes | `DEFERRED` | Outside the critical operational contract while the cache module remains experimental | Post-1.0; unscheduled | 2026-08-12 |

An application-level error categorizer may normalize runtime failures into
example:

```text
VALIDATION
OPERATOR_FAILURE
TIMEOUT
CANCELLATION
PERSISTENCE_FAILURE
LIFECYCLE_EXTENSION_FAILURE
UNKNOWN
```

These categories are not inferred by the infrastructure binders. Raw exception
messages and class names remain forbidden as default tags.

## Tag policy

The default `Gear4jMeterTagPolicy` emits no identifier tag. Completed run and
station metrics retain only the bounded `status` tag. This keeps the number of
time series independent of the number of generated pipelines, operations and
branches.

Use an explicit allowlist when selected identifiers are operationally useful:

```java
Gear4jMeterTagPolicy policy = Gear4jMeterTagPolicy.allowlistedIdentifiers(
        Set.of("checkout", "billing"),
        Set.of("validate", "persist"),
        Set.of("main", "fallback"));
```

Unknown identifiers are aggregated under `other`. A custom implementation can
provide other bounded dimensions. The deprecated `legacyIdentifiers()` policy
restores raw identifier tags only to support a controlled migration.
