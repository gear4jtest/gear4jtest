# 0003 - Pipeline reference invalidation is a loader/cache concern

## Status

Future direction. Not implemented.

## Context

Pipeline versions are expected to be immutable.

A reference to a concrete version is stable. Publishing a newer version should not change a compiled parent pipeline
that explicitly references an older version.

Aliases such as `latest` are mutable. A parent pipeline compiled while `latest` points to version `1.2.0` may need
invalidation when `latest` later points to `1.3.0`.

## Decision

Future external pipeline loading should distinguish:

- `declaredReference`: the reference written by the pipeline definition;
- `resolvedReference`: the concrete version selected at compile/load time.

A running pipeline graph must remain stable. Alias changes must affect future runs after cache invalidation, not mutate
a graph already being executed.

## Recommended behavior

Pinned reference:

```text
risk-scoring:1.2.0
```

Publishing `risk-scoring:1.3.0` should not invalidate this reference.

Alias reference:

```text
risk-scoring:latest
```

If the alias resolution changes, cached compiled graphs depending on that declared reference should be marked stale and
reloaded or recompiled before the next run.

## Dependency index

A loader or compiler should eventually track dependencies such as:

```text
checkout:2.0.0 depends on risk-scoring:latest resolved as risk-scoring:1.2.0
checkout:2.0.0 depends on address-normalization:3.4.0 resolved as address-normalization:3.4.0
```

This index enables targeted invalidation.

## Traceability

Execution traces should eventually record both declared and resolved references so historical runs remain explainable
after aliases move.

## Non-goal

Do not implement this inside the core execution strategy immediately.

The current important design point is to keep the execution model compatible with both declared and resolved references.
