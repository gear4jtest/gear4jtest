# Critical Micrometer observability — 7 August 2026

**Finding:** O-01 — Micrometer did not auto-bind the most failure-sensitive
generated-code and artifact infrastructure.

## Implemented contract

- end-to-end loading and compilation outcomes, deadlines, executor activity,
  queues and rejections;
- finite per-phase attempts, failures, cumulative duration and maximum duration
  for artifact read, translation, compilation, class loading, construction and
  dependency injection;
- explicit artifact-integrity failures based on a typed size/SHA-256 mismatch,
  never on exception-message parsing;
- classloader occupancy, protected aliases, bytecode weight, evictions and
  registration rejections;
- artifact-store operation outcomes, bytes and latency;
- spool occupancy, quota rejections, stale cleanup and cleanup failures;
- Spring Boot auto-binding only when each monitored type has one candidate.

## Cardinality budget

Infrastructure binders expose only `phase`, `outcome`, `result` and `operation`,
whose values are closed framework-owned sets. Pipeline IDs, hashes, bean names,
exception classes/messages and business content are excluded.

## Regression coverage

Tests verify every binder against a `SimpleMeterRegistry`, the finite tag keys,
artifact-integrity counting, phase failure/duration snapshots and conditional
Spring Boot registration.
