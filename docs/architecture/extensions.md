# Runtime extension architecture

## Status

Implemented, evolving.

## Intent

Runtime extensions let applications add behavior around Gear4J execution without hard-coding those concerns into the
core engine.

The SPI is intentionally split by scope. Avoid a single extension interface that can intercept everything.

## Extension types

| Extension                   | Scope                                           |
|-----------------------------|-------------------------------------------------|
| `RuntimeExtension`          | Base type and ordering contract.                |
| `RunInterceptorExtension`   | Wraps the whole run with around-run behavior.   |
| `RunLifecycleExtension`     | Observes run lifecycle events.                  |
| `StationWrapperExtension`   | Wraps station runners.                          |
| `StationLifecycleExtension` | Observes station lifecycle events.              |
| `ExecutorWrapperExtension`  | Decorates executors used by async runtime work. |

## Ordering

Extensions are resolved once before execution starts. Global extensions are
followed by assembly-line defaults and then request extensions; entries are
accumulated rather than overridden or deduplicated. The merged list is sorted
by `RuntimeExtension.getOrder()`, then by implementation class name. Equal
instances of the same implementation retain their source-list order.

When extensions wrap behavior, treat them as an onion:

- lower order is outermost for run interceptors, station wrappers and executor
  wrappers;
- each wrapper should call its delegate exactly once unless intentionally short-circuiting;
- failures should respect the extension's documented lifecycle failure mode.

Lifecycle observers use bracket ordering instead of wrapper construction:

- start callbacks run from higher order to lower order;
- terminal callbacks run from lower order to higher order;
- all run start callbacks are attempted before the first critical start failure
  is normalized, so every observer receives its matching completion callback;
- `RuntimeExtension.TERMINAL_OBSERVER_ORDER` and
  `RuntimeExtension.PERSISTENCE_ORDER` are reserved for built-in final-state
  observation and persistence respectively.

Consequently, ordinary critical lifecycle observers run before the built-in
terminal metrics observer on completion. Persistence runs last and receives the
fully normalized terminal snapshot. On start the order is reversed, so those
same built-ins establish their started state before ordinary application hooks.

## Failure impact

| Extension type | Non-fatal failure contract |
| --- | --- |
| Run interceptor | Normalized into a failed `ExecutionResult` after the run has started. |
| Station wrapper | Handled by the station exception boundary and normal error policy. |
| Executor wrapper | A failure while a station obtains its decorated executor is a station failure. |
| `BEST_EFFORT` lifecycle | Logged and ignored. |
| `CRITICAL` run lifecycle | Produces a failed run result; completion continues so later terminal observers see the failure. |
| `CRITICAL` station lifecycle | Marks the station failed and lets its parent `FlowConfig` decide propagation. |

JVM `Error` values are outside these recoverable contracts and escape without
being converted into ordinary extension or station failures.

Executor wrappers return non-owning views. They must not shut down a supplied
executor, and Gear4J does not shut down executors attached to container
definitions.

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
