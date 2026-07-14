# ADR 0024 - UUIDv7 generation uses bounded logical time

## Status

Accepted - 2026-07-14

## Context

The default UUIDv7 generator keeps a 12-bit sequence per thread so identifiers
remain ordered when several values are generated during the same millisecond.
The previous implementation waited in a `Thread.onSpinWait()` loop after all
4096 sequence values were consumed. If the wall clock had moved backwards or
remained frozen, generation consumed a CPU core until the clock caught up.

Gear4J needs generation to remain bounded because identifiers are created on
runtime execution paths. Blocking for the duration of an NTP correction, VM
snapshot restore or host clock anomaly is less acceptable than a small temporary
difference between the UUID timestamp and wall time.

## Decision

- Read the wall clock once for each generated identifier.
- Preserve a timestamp and 12-bit sequence in thread-local state.
- Use the newer wall-clock timestamp and reset the sequence when wall time has
  advanced beyond logical time.
- Continue the sequence when wall time is equal to or behind logical time.
- After sequence value 4095, advance the thread-local logical timestamp by one
  millisecond and reset the sequence instead of waiting or polling.
- Reject timestamps outside the unsigned 48-bit UUIDv7 millisecond range.
- Keep the public `IdGenerator` SPI unchanged. The injectable time source is
  package-private and exists to make rollback and frozen-clock behavior
  deterministic in tests.

## Consequences

UUID generation no longer spins or waits for wall-clock recovery. Identifiers
remain monotonic within a generator thread and retain UUID version 7 and RFC 4122
variant bits. Under sustained generation while the clock is frozen or rolled
back, the encoded timestamp can temporarily move ahead of real time by one
millisecond per 4096 generated identifiers. Once wall time moves strictly beyond
the logical timestamp, it becomes authoritative again and the sequence resets.

The default generator still relies on random low bits to make identifiers from
different threads practically collision-free. Applications requiring a stronger
cross-process allocation contract may continue to provide a custom
`IdGenerator`.
