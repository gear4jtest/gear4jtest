# Micrometer observability

## Status

Partially implemented.

The `gear4jtest-micrometer` module is an initial optional integration. It is not
yet the complete production observability module.

## Current scope

The current module registers lifecycle, duration and persistence
signals. It is useful to confirm that Gear4J is active, understand completed
run/station latency and verify that persistence buffers are not silently
accumulating.

See the module README for the exact metric names:

```text
gear4jtest-micrometer/README.md
```

The built-in duration timers are recorded only when both start and end
timestamps are present and consistent. Missing or inverted timestamps are
ignored rather than emitting misleading zero or negative durations.

## Future production metric surface

A richer module should expose counters and timers for:

- pipeline outcomes: succeeded, failed, stopped, cancelled;
- station outcomes: succeeded, failed, skipped, stopped, cancelled;
- timeout and cancellation counts;
- event reaction results: completed, failed, dropped;
- persistence flush duration, failures and backlog;
- parallel branch rejections and branch durations.

## Error categorization

Metrics should not expose raw exception messages. A future error categorizer
should normalize failures into stable low-cardinality values such as:

```text
VALIDATION
OPERATOR_FAILURE
TIMEOUT
CANCELLATION
PERSISTENCE_FAILURE
LIFECYCLE_EXTENSION_FAILURE
UNKNOWN
```

Users can still implement their own `RuntimeExtension` if they need custom
metrics or application-specific tagging.

## Cardinality policy

Metric tags must remain configurable. Tags such as `pipeline.id` and
`operation.id` can be extremely useful, but they may explode cardinality in a
large BO-driven environment.

The future default should be safe, with opt-in detailed tags:

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


## Tag cardinality

The default Micrometer extension keeps the historical tags such as `pipeline.id`,
`operation.id`, `branch.id` and `status`. Applications that generate dynamic
pipeline or operation identifiers should avoid exporting unbounded-cardinality
tags. They can provide a custom `Gear4jMeterTagPolicy` bean to control the exact
set of tags emitted by the Micrometer extension.
