# Runtime saturation qualification — phase 14

**Date:** 12 August 2026
**Scope:** JDBC readiness pool acquisition and shared event-dispatch fairness

## Objective

Close the two remaining phase-2 runtime-load qualifications from the 2025 audit:

- saturated/slow JDBC connection acquisition must not make the global readiness
  probe unbounded;
- asymmetric multi-run event load must not let one loud run monopolize a shared
  dispatcher worker.

This phase does not add durable event delivery, a production latency SLO or the
post-1.0 advanced supply-chain work.

## Evidence and correction

`DatabaseExecutionManagerConnectivityLoadTest` holds
`DataSource#getConnection()` indefinitely and ignores interruption. The first
readiness probe returns `CONNECTIVITY_UNAVAILABLE` within a portable one-second
test envelope around a 100 ms configured budget. A second probe also returns
without queuing, and the datasource records exactly one acquisition attempt.

`EventManagerMultiRunLoadTest` pins a single dispatcher worker, queues 256 loud
events and one quiet event while that worker is blocked, then releases it. The
quiet reaction is submitted after no more than the 64-event service slice and
within a generous two-second test envelope. All 257 reactions complete with no
event drops.

The shutdown side of the JDBC acceptance criterion remains covered by
`DatabaseExecutionManagerShutdownTest`: a retrying database-unavailable path and
a JDBC write that ignores interruption both return on the single monotonic
deadline. The assertions preserve the initial, flushed and remaining record
counts, attempt count, per-run failure and retained diagnostic buffer. Phase 14
re-audited that path and does not reopen or weaken its exact loss report.

The implementation follows the measured boundaries:

- one no-queue daemon connectivity worker per persistence runtime bounds the
  complete readiness caller and prevents probe-thread accumulation;
- each run-local dispatcher task drains at most 64 events before re-enqueuing at
  the shared queue tail.

## Preserved contracts

- The event runtime remains bounded, non-blocking, in-memory and best-effort.
- No public API or event-ordering guarantee is added.
- The datasource acquisition timeout remains operationally mandatory for worker
  reclamation and normal JDBC writes.
- Shutdown retains exact persistence drain/loss reporting; the readiness worker
  never owns buffered persistence records.
- Existing public symbols, build tasks, dependency policy and coverage
  thresholds are unchanged.

## Sandbox result

The affected core and JDBC production trees compiled in full with Java 17. Both
new JUnit sources compiled against API stubs, and all 815 repository Java
sources passed an independent parser. Executable harnesses using the compiled
production classes reported:

```text
EVENT_FAIRNESS_PASS loudAtQuiet=64 quietLatencyMicros=4617 completed=257
JDBC_PROBE_PASS firstMillis=109 secondMicros=380 acquisitions=1
```

Repository-local Markdown links, ADR identifiers, JSON files, shell syntax and
changed-file whitespace also passed their disconnected checks. Gradle execution
was unavailable because the sandbox could not reach the Gradle 9.6.1
distribution host; this is an environmental limitation, not a passing Gradle
result.

## Validation

Run the focused qualifications and the complete verification path:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*EventManagerMultiRunLoadTest'
./gradlew :gear4jtest-jdbc:test --tests '*DatabaseExecutionManagerConnectivityLoadTest'
./gradlew check
```

When dependency resolution is unavailable, compile all affected Java 17
production sources, parse every changed Java source and run equivalent
standalone saturation harnesses. Record that environmental limitation instead
of treating disconnected Gradle execution as a product failure.
