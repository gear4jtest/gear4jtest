# Micrometer observability

## Status

Partially implemented.

The `gear4jtest-micrometer` module is an initial optional integration. It is not
yet the complete production observability module.

## Current scope

The current module registers low-cardinality lifecycle and persistence signals.
It is useful to confirm that Gear4J is active and that persistence buffers are
not silently accumulating.

See the module README for the exact metric names:

```text
gear4jtest-micrometer/README.md
```

## Future production metric surface

A richer module should expose counters and timers for:

- pipeline outcomes: succeeded, failed, stopped, cancelled;
- pipeline duration by outcome;
- station outcomes: succeeded, failed, skipped, stopped, cancelled;
- station duration by status and station kind;
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
