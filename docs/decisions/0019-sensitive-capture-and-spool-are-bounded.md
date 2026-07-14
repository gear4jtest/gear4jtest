# 0019 — Sensitive capture and artifact spooling are explicit and bounded

## Status

Accepted.

## Context

Persistence managers previously defaulted to `SensitiveDataRedactor.none()`.
Enabling persistence outside Spring therefore captured run context, input,
result, station context and error messages without an explicit confidentiality
decision. Artifact spools had a private configurable directory and per-artifact
limits, but no aggregate quota, startup cleanup or residual occupancy signal.

## Decision

- `InMemoryExecutionManager` and `DatabaseExecutionManager` default to
  `SensitiveDataRedactor.discardSensitiveValues()`.
- The metadata-only policy stores empty context maps and discards inputs,
  results and error messages. Identifiers, statuses and timestamps remain.
- Passing `SensitiveDataRedactor.none()` is the explicit opt-in for unredacted
  capture. A custom redactor is the preferred production choice.
- `PersistenceConfiguration.storeResultObject(false)` is now enforced when the
  final run trace is built; it does not remove the value returned to the caller.
- Spring Boot now defaults to `DISCARD`, matching direct persistence managers.
  `REQUIRE` fails without an effective bean and `DISABLED` explicitly permits
  raw capture. `WARN` remains temporarily as a deprecated compatibility mode
  that supplies a no-op redactor and logs a warning.
- Artifact stores use an `ArtifactSpoolPolicy`. Its defaults are a 100 MiB quota
  per managed spool and deletion at initialization of `.tmp` files older than
  24 hours.
- Managed spool writes reserve quota before writing each block. Rejected or
  failed operations remove their temporary file.
- Existing non-stale residues count toward the quota. Their file count and bytes,
  stale cleanup, quota rejections and cleanup failures are exposed through
  `ArtifactSpoolMonitor`.
- The spool directory and every managed file retain owner-only POSIX permissions
  when the platform supports them. Symbolic-link directories and files outside
  the configured directory are rejected.

## Consequences

This is an intentional pre-1.0 behavior change for direct manager and Spring
Boot users. Applications that require full audit payloads must now configure a
redactor, explicitly choose `SensitiveDataRedactor.none()`, or set Spring Boot
`redaction-mode=DISABLED`.

The spool quota is local to each managed store instance. Operators should still
set filesystem/container limits and avoid sharing the configured directory with
unrelated applications. Cleanup is age-based; the default 24-hour threshold is
chosen to avoid treating ordinary in-flight writes as residues.

No database schema or migration change is required.
