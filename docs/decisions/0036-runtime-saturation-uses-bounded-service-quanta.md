# 0036 — Runtime saturation uses bounded service quanta

## Status

Accepted — 2026-08-12

## Context

Two phase-2 audit qualifications remained open. A run-local event dispatch task
could drain its complete queue before yielding a shared dispatcher worker, so an
asymmetric loud run delayed an already-scheduled quiet run. The JDBC readiness
configuration reached `Statement#setQueryTimeout` only after
`DataSource#getConnection()`, so saturated pool acquisition could exceed the
advertised probe duration.

Neither runtime is a durable work queue. The corrective boundary therefore has
to remain bounded, non-blocking and explicit about what interruption cannot
guarantee.

## Decision

- A run-local event dispatch task processes at most 64 events. If events remain,
  the run submits a new task at the tail of the bounded shared queue. Rejection
  retains the existing best-effort drop-and-count behavior.
- The slice is an event-count fairness quantum, not a wall-clock SLO and not an
  ordering guarantee across runs.
- Each JDBC persistence runtime owns a lazy single-worker daemon executor with
  no task queue for connectivity checks. The configured probe timeout bounds the
  caller across acquisition and statement execution.
- A concurrent probe, or a later probe while a non-cooperative call still owns
  the worker, fails fast as connectivity unavailable. Shutdown interrupts the
  worker.
- Applications must still configure their datasource acquisition timeout. It
  is what eventually reclaims a worker when a driver ignores interruption and
  it protects normal writes outside the readiness path.

## Consequences

Under a pinned single dispatcher worker, an already-scheduled quiet run reaches
reaction submission after at most 64 loud-run events rather than after the loud
queue drains completely. A re-enqueue adds bounded dispatcher-task traffic and
can itself be rejected under global saturation, consistent with the documented
best-effort contract.

Readiness callers no longer wait indefinitely for pool acquisition. At most one
daemon per persistence runtime can remain inside a non-cooperative JDBC call;
overlapping probes report unavailable instead of accumulating threads. The
configured duration remains a portable guardrail rather than a claim that an
uncooperative driver operation has been terminated.
