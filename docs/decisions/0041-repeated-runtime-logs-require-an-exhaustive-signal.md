# 0041 - Repeated runtime logs require an exhaustive signal

## Status

Accepted - 2026-08-13

## Context

The best-effort event runtime counted queue, dispatcher, reaction and failure outcomes but also emitted one warning or
error for every occurrence. Under sustained saturation or a failing subscription, logging could amplify the incident
through allocation, stack rendering and output I/O. Globally disabling those levels would remove the first actionable
diagnostic, while silently sampling failures without another signal would make loss impossible to quantify.

## Decision

- Treat metrics, runtime statistics, current health and terminal reports as exhaustive operational signals. Logs remain
  representative diagnostics.
- Rate-limit only repeated paths that update an exhaustive signal before the log decision.
- For counted event-manager rejection and failure categories, emit the first occurrence immediately and at most one
  reminder per category per JVM per monotonic minute.
- Include the number of occurrences suppressed since the previous emission. Do not add payloads, context values or
  business results to the representative log.
- Keep severity unchanged: recoverable saturation/rejection is `WARN`; unexpected submission or user callback failure
  is `ERROR`.
- Leave paths without an equivalent exhaustive signal unsuppressed until such a signal exists.

## Consequences

An event saturation incident produces bounded framework logging while all dropped and failed work remains visible in
`EventRuntimeStats` and `EventRuntimeMetrics`. Log-line counts no longer equal occurrence counts, so alerting and
capacity decisions must use metrics. The limiter is internal, uses no scheduler and introduces no public configuration
or new meter dimension.
