# Runtime logging strategy

## Status

Implemented for the Gear4J 1.0 runtime surface.

## Purpose

Logs provide representative diagnostics for configuration, lifecycle transitions and unexpected failures. They are not
the authoritative counter for high-volume runtime activity. Gear4J metrics, runtime statistics, health indicators and
shutdown reports retain the complete operational state where those contracts exist.

## Severity contract

| Level | Gear4J use |
| --- | --- |
| `DEBUG` | Opt-in execution or cache diagnostics that are too frequent for normal production output. |
| `INFO` | Infrequent operator-visible transitions, currently schema migration and baseline actions. |
| `WARN` | Recoverable degradation, rejected best-effort work, unsafe explicit compatibility modes or incomplete cleanup. |
| `ERROR` | Unexpected infrastructure/extension failures or terminal loss that requires investigation. |

Normal station completion, skip and business failure are represented by `ExecutionResult`, traces and metrics rather
than one framework log per station. Gear4J production code does not write directly to `System.out` or `System.err`.

## Repeated event-runtime signals

The in-memory event runtime can reject or fail many events in a short saturation window. Logging every occurrence would
make the diagnostic path compete for CPU, allocation and I/O with recovery. The following process-wide categories emit
the first occurrence immediately and then at most one reminder per minute:

- run-local event queue rejection;
- shared dispatcher rejection;
- reaction-executor rejection or unexpected submission failure;
- reaction handler failure;
- reaction predicate failure.

Each reminder includes `suppressedSincePreviousEmission`. The limiter uses monotonic time and bounds only log emission;
it never controls event flow. Every occurrence still updates the run-local `EventRuntimeStats`, process-wide
`EventRuntimeMetrics` or dispatcher statistics before any log decision. Consequently, alerts must use metrics rather
than count matching log lines.

The limiter is shared by category across the JVM. Event and subscription type names remain only on representative log
entries; payloads, context values, results and error messages are not added as structured dimensions.

## Signals that remain unsuppressed

Gear4J does not suppress a repeated diagnostic unless an exhaustive counter, current-state health contract or terminal
report remains available. Shared dispatcher task failures, artifact replication/self-healing failures, persistence
observer failures and similar callbacks therefore retain their individual log today. Adding a bounded public counter is
a prerequisite to rate-limiting one of those paths in a later change.

Per-run persistence shutdown retries also remain visible with run id, attempt and remaining-record count. The terminal
`PersistenceShutdownReport` is the authoritative result, but individual attempts are intentionally useful during the
bounded shutdown interval.

## Application guidance

- Keep `INFO` as the normal production threshold for Gear4J packages.
- Alert on bounded metrics and health state; use logs to investigate a representative occurrence.
- Enable `DEBUG` temporarily for a focused execution/cache diagnosis, not as permanent high-volume telemetry.
- Preserve the application logging framework's exception rendering and retention policy; Gear4J depends only on SLF4J.
- Never rely on Gear4J logs as a durable audit trail or guaranteed event-delivery record.
