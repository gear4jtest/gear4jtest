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

## Exception confidentiality

Framework exception logs use a protected default, independently from persistence
capture redaction. Before passing a failure to SLF4J, `ExceptionDiagnostics`
replaces it with a diagnostic containing only the original exception class name
and an indication that details were withheld. The replacement has no original
message, cause, suppressed exceptions or stack frames, and retains no reference
to the original exception graph. It does not call a user exception's `getMessage`
or `toString` methods.

This policy covers runtime/lifecycle/event failures, JDBC flush diagnostics,
external loading/store cleanup, experimental cache failures and XML formatter
failures. Existing run/station identifiers, callback names, levels, rate limits
and counters remain available. Original exceptions stay in the returned result,
trace or propagated failure according to their normal execution contract.

Raw exception diagnostics require this explicit JVM property:

```text
-Dgear4j.logging.includeExceptionDetails=true
```

Only `true`, ignoring case, enables details. The absent property, unrecognized
values and denied property access retain protection. The setting is process-wide
and evaluated at each exception-log call; it is a JVM system property, not a
Spring Boot configuration property. Enable it only for a reviewed diagnostic
window with appropriate log access and retention. It exposes original messages,
stack frames, causes and suppressed exceptions. Enabling `DEBUG` alone does not
disable protection. An application may alternatively log an authorized result's
exception through its own diagnostic channel.

`SensitiveDataRedactor` and `gear4j.persistence.redaction-mode` affect persistence
capture, not this setting. Event payload policies form another independent
boundary. This is not a general log scrubber: application logs, dependency-owned
logs, user-supplied MDC values and identifiers passed as structured arguments
remain the application's responsibility. Do not put secrets in identifiers or
MDC. See [ADR 0046](../decisions/0046-exception-logs-require-explicit-detail-opt-in.md).

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
- Configure the logging framework's retention policy; protected exception summaries are the default even with a
  renderer that normally prints full exceptions. Gear4J depends only on SLF4J.
- Never rely on Gear4J logs as a durable audit trail or guaranteed event-delivery record.
