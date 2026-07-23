# ADR 0022 - Phase 6 bounded runtime and quality ratchets

## Status

Accepted - 2026-07-13

## Context

The audit identified an unbounded shared event-dispatch queue, an unbounded experimental cache that shared mutable
outputs, globally applied unused build plugins, implicit aggregate coverage execution and a monolithic four-container
JDBC test. Performance and branch coverage had reports but no versioned failure thresholds.

## Decision

- Bound the shared dispatcher drain-task queue at 4,096 by default and use non-blocking rejection. Preserve best-effort
  semantics by dropping/counting affected run-local events rather than blocking business threads.
- Implement the experimental cache as a synchronized access-ordered bounded repository with TTL cleanup, entry/weight
  limits, clone-on-write/read and observable statistics. Unknown mutable values are rejected safely by default.
- Apply JMH only to the core-hosted benchmark harness and version portable latency, throughput, allocation, heap and
  thread guardrails.
- Enforce branch ratchets for four initial critical classes from combined unit/integration data, while keeping aggregate
  report generation explicit. Phase 12 expands this to nine critical classes and adds six module-level line floors.
- Run PostgreSQL on every normal verification path and distribute all four JDBC dialects in scheduled/main/release CI.

## Consequences

Cache users storing mutable values must now provide an appropriate `PayloadCloner`; rejected cache writes remain visible
but do not fail a successful run. Shared event overload is bounded and observable locally; process-wide aggregation was
added by phase 7. Performance thresholds require confirmation and incremental tightening on the pinned
CI runner before they can be interpreted as regression baselines. The module remains marked experimental until its API
compatibility is decided for 1.0.
