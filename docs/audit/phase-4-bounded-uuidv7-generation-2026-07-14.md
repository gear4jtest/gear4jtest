# Phase 4 — Bounded UUIDv7 generation

**Date:** 14 July 2026
**Scope:** default UUIDv7 clock source, sequence exhaustion and rollback behavior

## Objective

Remove the active wait in `DefaultUuidGenerator` so a frozen or rolled-back wall
clock cannot block identifier generation or consume a CPU core after the 12-bit
sequence is exhausted.

## Changes

### Logical timestamp instead of clock polling

`DefaultUuidGenerator` now reads its time source once per identifier. Each thread
continues to keep an independent timestamp and 12-bit sequence:

- a newer wall-clock millisecond resets the sequence to zero;
- an equal or older wall-clock value continues the current sequence;
- after sequence value 4095, the logical timestamp advances by one millisecond
  and the sequence resets to zero.

The previous `Thread.onSpinWait()` loop has been removed. Generation therefore
has no dependency on the duration of an NTP correction, VM snapshot rollback or
frozen clock.

### Testable time source

The static public API remains unchanged:

```java
UUID id = DefaultUuidGenerator.generate();
```

The implementation now delegates to a generator instance with a `LongSupplier`
time source. Its constructor and instance method are package-private, allowing
unit tests in the same package to inject deterministic clocks without expanding
the supported public API.

### UUIDv7 range validation

The generator rejects negative milliseconds and values outside the unsigned
48-bit UUIDv7 timestamp range. Exhausting the final representable logical
millisecond also fails explicitly instead of wrapping the timestamp bits.

## Tests

`DefaultUuidGeneratorTest` now covers:

- concurrent uniqueness, UUID version and RFC variant;
- 4097 identifiers while the clock is frozen;
- a five-second rollback after the first identifier;
- exactly one clock read per generated identifier, proving that no polling loop
  remains;
- sequence reset after logical timestamp advancement;
- return to wall-clock time once it moves beyond logical time;
- invalid wall-clock and logical timestamp range rejection.

The frozen and rolled-back clocks throw immediately if the generator attempts an
extra read, making the no-polling regression deterministic rather than dependent
on timing probability.

## Operational semantics

The chosen tradeoff is bounded latency over exact wall-clock fidelity during an
anomaly. At a frozen clock, one logical millisecond is added per 4096 identifiers
on each thread. The timestamp may therefore lead wall time temporarily. It never
moves backwards within a generator thread, and wall time becomes authoritative
again as soon as it is strictly newer.

This decision is recorded in
[`ADR 0024`](../decisions/0024-uuidv7-uses-bounded-logical-time.md).

## Validation

The production class was compiled with:

```bash
javac --release 17 DefaultUuidGenerator.java
```

A standalone Java 17-compatible harness verified frozen-clock exhaustion,
multi-second rollback, wall-clock catch-up, invalid timestamps and 40,000 UUIDs
produced concurrently. Its result was:

```text
frozenClockWallMs=2 cpuMs=10 clockReads=4097
phase4-smoke=OK
```

The focused JUnit test source was also compiled with minimal JUnit and AssertJ
API stubs to validate Java syntax and generic overload resolution.

Recommended repository validation commands:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*DefaultUuidGeneratorTest'
./gradlew check
```

The Gradle distribution could not be downloaded in the implementation
environment because `services.gradle.org` was not resolvable. The commands above
must therefore be executed in the user's workspace.
