# 0039 - Runtime latency metrics use bounded observations

## Status

Accepted - 2026-08-13

## Context

Gear4J already exposes run/station timers and cumulative persistence counters,
but operators cannot separate parallel-branch latency from ordinary station
latency or inspect the distribution of JDBC flush attempts. Adding internal
executor, buffer or repository types to the Micrometer module would make the
optional integration depend on storage-provider and engine implementation
details. Adding raw branch identifiers would also make meter cardinality grow
with generated pipelines.

The station lifecycle SPI already carries immutable station records, including
an optional branch ID, and already receives synthetic terminal outcomes for
branches that never entered a worker. Persistence monitoring needs one similarly
small active-observation contract because cumulative counters cannot populate a
Micrometer `Timer` histogram.

## Decision

- Derive branch meters from the existing `StationLifecycleExtension`. A
  non-null branch ID identifies branch work; no new engine hook or engine type is
  exposed.
- Count synthetic skipped, cancelled, interrupted and failed-before-start
  branches as terminal completions. Count executor rejection only when the
  failure is a `RejectedExecutionException` before branch start.
- Record branch duration only for ordinary executed branches with consistent
  timestamps. A synthetic terminal record has no execution duration.
- Reuse `Gear4jMeterTagPolicy`. The default branch surface has no identifier
  tags and uses only the finite terminal `status`; reviewed allowlists may add
  selected identifiers.
- Add `PersistenceRuntimeMonitor.subscribeToFlushes(...)` with a removable
  subscription and an immutable `PersistenceFlushObservation`. Trigger and
  outcome are closed enums owned by Gear4J.
- Measure elapsed time with `System.nanoTime()`. Asynchronous duration begins at
  admission and therefore includes executor queue delay. Empty explicit or
  terminal flush calls are not observations; executor rejection is.
- Invoke flush observers after buffer/JDBC work releases its locks. Observer
  runtime exceptions are logged and isolated from persistence and from other
  observers; fatal JVM errors retain their normal propagation semantics.
- Publish `gear4j.persistence.flush.duration{trigger,outcome}` as a Micrometer
  timer with percentile histograms enabled. Spring owns the returned
  subscription and removes it when the registrar bean closes.

## Bounded dimensions

| Dimension | Values |
| --- | --- |
| `status` | finite `StationLogStatus` values |
| `trigger` | `async`, `explicit`, `terminal`, `shutdown` |
| `outcome` | `succeeded`, `failed`, `rejected`, `timed_out`, `interrupted` |

No exception class, message, run ID, hash or business value is emitted by the
new infrastructure timer.

## Consequences

Operators can graph branch throughput/rejections separately from station work
and can use backend p95/p99 calculations for real JDBC flush attempts. The core
module remains independent of Micrometer and JDBC; other persistence providers
may implement the same observation contract or retain the default no-op
subscription.

The subscription adds a small callback cost only when an observer is present.
Observers must still be fast and non-blocking even though their ordinary runtime
exceptions cannot change persistence outcomes. No database schema or migration
changes are introduced.
