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
gear4j.stations.started
gear4j.stations.completed
gear4j.persistence.buffered.station.logs
gear4j.persistence.active.runs
```

These metrics are useful to confirm that Gear4J is active and that persistence is
not silently accumulating logs, but they are intentionally conservative.

## Why the current surface is conservative

Metrics with tags such as `pipelineId`, `operationId`, `exceptionClass` or
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

gear4j.pipeline.duration
  tags: pipeline.id, pipeline.version, outcome

gear4j.station.executions
  tags: station.kind, operation.type, status

gear4j.station.duration
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

## Tag policy direction

A future `MetricsTagPolicy` should let users decide which tags are emitted:

```yaml
gear4j:
  metrics:
    tags:
      include-pipeline-id: true
      include-pipeline-version: true
      include-operation-id: false
      include-operation-type: true
      include-error-category: true
```

The default should stay safe for common monitoring backends.
