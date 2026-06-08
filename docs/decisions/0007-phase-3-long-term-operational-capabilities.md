# 0007 — Phase 3 operational hardening

## Status

Accepted.

## Context

After the phase 1 packaging/schema pass and the phase 2 runtime semantics pass,
Gear4J needs a pragmatic operational hardening pass before more feature work.
The goal is not to reopen heavy supply-chain tooling or to redesign the runtime.
The goal is to make the existing release surface easier to operate and diagnose.

The phase focuses on:

- low-cardinality Micrometer duration metrics;
- clearer JDBC persistence exceptions;
- avoiding unnecessary `SELECT *` in repository list queries;
- small build hygiene cleanups;
- documenting public SPI contracts.

## Decision

### Micrometer duration timers

`gear4jtest-micrometer` now records timers for completed pipeline runs and
station executions in addition to the existing counters:

```text
gear4j.runs.duration
gear4j.stations.duration
```

Durations are recorded only when both timestamps are present and the end time is
not before the start time. Invalid/incomplete timestamps are ignored instead of
emitting misleading zero or negative samples.

### JDBC repository diagnostics

`DatabaseAssemblyRunRepository` now wraps SQL and JSON mapping failures in
`ExecutionPersistenceException` with the operation being attempted and the
configured dialect. This keeps failures actionable without forcing callers to
reverse-engineer which repository method failed from a raw `SQLException`.

Read/list queries now project the columns consumed by the mapper instead of
using `SELECT *`. `findById` still reads the same logical record, but the SQL
surface is explicit and less sensitive to future wide columns.

### Build hygiene

The deprecated Sonar property `sonar.language` is removed. Language detection is
left to Sonar's current analyzer behavior.

Dependency verification remains intentionally out of this pass. It is still a
release-hardening topic, but the project decision is to avoid spending more time
on heavy supply-chain machinery until the runtime/build/release process is more
stable.

### SPI documentation

Public extension contracts should state at least their nullability,
thread-safety and lifecycle expectations. Phase 3 adds baseline Javadoc to the
most visible SPI/repository contracts so custom implementations have clearer
rules.

## Consequences

- Operators get latency metrics without pulling Micrometer into the core module.
- Persistence failures expose more useful context to application logs.
- Repository SQL is less fragile when schema columns are added later.
- The build stops carrying a deprecated Sonar property.
- Public extension points are easier to implement safely.
