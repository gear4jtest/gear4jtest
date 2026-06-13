# 0003 - Pipeline reference invalidation is a loader/cache concern

## Status

Partially implemented for local `latest` classloader aliases in `AssemblyLineManager`. Distributed invalidation and
compiled dependency indexes remain future work.

## Context

Pipeline versions are expected to be immutable.

A reference to a concrete version is stable. Publishing a newer version should not change a compiled parent pipeline
that explicitly references an older version.

Aliases such as `latest` are mutable. A parent pipeline compiled while `latest` points to version `1.2.0` may need
invalidation when `latest` later points to `1.3.0`.

## Decision

External pipeline loading distinguishes, and future external formats should continue to distinguish:

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

When a RUN publication or promotion can change `latest`, `AssemblyLineManager` clears the local latest classloader alias.
The next latest lookup resolves the repository again and points the alias to the newly resolved concrete loader id.
Pinned concrete loaders remain cached.

## Dependency index

A loader or compiler should eventually track transitive compiled dependencies such as:

```text
checkout:2.0.0 depends on risk-scoring:latest resolved as risk-scoring:1.2.0
checkout:2.0.0 depends on address-normalization:3.4.0 resolved as address-normalization:3.4.0
```

This index enables targeted invalidation.

## Traceability

Core pipeline-call traces record declared and resolved references when a `ResolvedPipelineTarget` is used, so historical
runs can remain explainable after aliases move. External formats should emit resolved targets when they support nested
pipeline references.

## Non-goal

Do not implement this inside the core execution strategy immediately.

The current important design point is to keep the execution model compatible with both declared and resolved references.
