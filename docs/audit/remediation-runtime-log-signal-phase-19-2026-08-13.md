# Runtime log signal-to-noise remediation - phase 19

**Date:** 13 August 2026
**Scope:** production logging inventory and event-runtime flood control

## Objective

Complete the 1.0 logging review by separating exhaustive operational signals from representative diagnostics and by
preventing best-effort event saturation from creating a secondary log storm.

## Inventory result

The production Java tree contains no direct console or stack-trace writes. The source inventory contains 0 `TRACE`, 11
`DEBUG`, 3 `INFO`, 28 `WARN` and 14 `ERROR` SLF4J call sites; normal station completion does not generate one framework
log per station. The only path where a bounded high-volume outcome was both counted and logged once per occurrence was
the in-memory event runtime.

Configuration/startup warnings, schema migration transitions and bounded shutdown diagnostics remain unchanged.
Artifact replication and persistence observer failures also remain unsuppressed because a log is still their only
complete failure signal.

## Delivered control

- Internal `PeriodicLogLimiter` uses monotonic time, emits the first signal immediately and returns the suppressed count
  with the next permitted reminder.
- Event queue/dispatcher rejection, reaction submission rejection/failure, handler failure and predicate failure share
  one limiter per category across the JVM.
- Each category emits at most once per minute after its first occurrence.
- Run-local and process-wide counters update before log admission and remain exhaustive.
- Unit tests cover first/reminder behavior, suppressed counts, nano-time overflow and invalid intervals. An event-runtime
  regression proves that every rejected reaction is still counted while repeated logs are suppressed.
- ADR 0041 and the runtime logging architecture define severity, sensitive-data and alerting rules.

## Validation commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*PeriodicLogLimiterTest' --tests '*EventManagerFailureTest'
./gradlew check
```

## Sandbox validation status

The configured Gradle 9.6.1 distribution is not cached in the audit sandbox, so the connected commands above remain the
merge gate. Offline Java 17 validation compiles the complete core production tree, parses every repository Java source
and runs an executable saturation harness: 20 rejected reactions produce one representative log while both run-local
and process-wide dropped-reaction counters advance by 20. Documentation links, ADR identifiers, trailing whitespace and
final newlines are checked as well.

This phase adds no dependency lockfile, Gradle verification metadata, durable outbox/spool replay, JPMS descriptor,
distributed quota or database migration. Those deferred or conditional decisions remain unchanged.
