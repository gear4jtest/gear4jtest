# Runtime extension architecture

## Status

Implemented, evolving.

## Intent

Runtime extensions let applications add behavior around Gear4J execution without hard-coding those concerns into the core engine.

The SPI is intentionally split by scope. Avoid a single extension interface that can intercept everything.

## Extension types

| Extension | Scope |
| --- | --- |
| `RuntimeExtension` | Base type and ordering contract. |
| `RunInterceptorExtension` | Wraps the whole run with around-run behavior. |
| `RunLifecycleExtension` | Observes run lifecycle events. |
| `StationWrapperExtension` | Wraps station runners. |
| `StationLifecycleExtension` | Observes station lifecycle events. |
| `ExecutorWrapperExtension` | Decorates executors used by async runtime work. |

## Ordering

Extensions are resolved before execution starts.

When extensions wrap behavior, treat them as an onion:

- lower order should generally be outermost;
- each wrapper should call its delegate exactly once unless intentionally short-circuiting;
- failures should respect the extension's documented lifecycle failure mode.

## Design rules

- Keep extensions focused.
- Prefer multiple narrow extensions over one broad extension.
- Do not leak extension internals into station strategies.
- Keep framework integration in optional modules such as `gear4jtest-spring`.
- Avoid adding dependencies to core only to support a specific extension implementation.

## Common extension use cases

- persistence observation;
- MDC or tracing context propagation;
- metrics;
- station wrapping for cross-cutting behavior;
- executor decoration for async context propagation;
- run lifecycle hooks.
