# gear4jtest-micrometer

Optional Micrometer instrumentation for Gear4J runtime lifecycle events.

The core runtime has no Micrometer dependency. Add this module only when you want
metrics and when your application already exposes a Micrometer `MeterRegistry`.

## Status

Initial observability module.

The module currently proves the integration point and exposes basic lifecycle and
persistence gauges/counters. It should not yet be considered a complete
production observability story.

## Current metrics

The initial metric surface focuses on low-cardinality runtime signals:

```text
gear4j.runs.started
gear4j.runs.completed
gear4j.runs.duration
gear4j.stations.started
gear4j.stations.completed
gear4j.stations.duration
gear4j.persistence.buffered.station.logs
gear4j.persistence.buffered.station.logs.oldest.age.seconds
gear4j.persistence.active.runs
gear4j.persistence.flushes.scheduled
gear4j.persistence.flushes.completed
gear4j.persistence.flushes.failed
gear4j.persistence.appends.rejected
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
```

These metrics are useful to confirm that Gear4J is active, track completed run
and station latency, and ensure that persistence and the best-effort event
runtime are not silently accumulating, saturating, running late reactions or dropping work. They are still
intentionally conservative.

`PersistenceMetricsBinder` is auto-registered by the Spring Boot starter when a
`DatabaseExecutionManager` and a `MeterRegistry` are available. The starter also calls
`EventMetricsBinder.bindProcessWide(...)` once for the tag-free aggregate. Per-run binding remains available explicitly
through `EventMetricsBinder.bind(...)` for diagnostics that own a specific `EventManager` lifecycle.

## Why the current surface is conservative

Metrics with tags such as `assemblyLineId`, `operationId`, `exceptionClass` or
`stationId` can quickly create high-cardinality time series, especially if
pipelines are generated dynamically by a BO.

The first module therefore avoids exposing detailed tags by default. Richer
metrics should be added with an explicit tag policy so applications can choose the
right trade-off between diagnostic value and backend cost.

## Future richer observability

A more complete Micrometer module should add:

```text
gear4j.pipeline.runs
  tags: pipeline.id, pipeline.version, outcome

gear4j.station.executions
  tags: station.kind, operation.type, status

gear4j.event.reactions
  tags: status=completed|failed|dropped

gear4j.persistence.flush.duration
gear4j.persistence.flush.failures
gear4j.persistence.buffer.size
gear4j.parallel.rejected.tasks
gear4j.parallel.branch.duration
```

The future design should also introduce an error categorization layer, for
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

Avoid exposing raw exception messages as metric tags. Exception class names may
also be too high-cardinality depending on the application and should be
configurable.

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
