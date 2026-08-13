# 0042 - Runtime contracts use bracketed lifecycle and cooperative boundaries

## Status

Accepted - 2026-08-13

## Context

The final 1.0 review still listed seven related contract questions: extension
ordering and failures, artifact/package naming, skip outcomes, run timing,
exception conversion, container behavior and cancellation checkpoints. Most of
the implementation already behaved consistently, but four gaps remained:
executor wrappers were composed in the opposite direction from run/station
wrappers, early metrics observers could miss a later critical lifecycle failure,
the engine rewrote a critical completion timestamp after terminal observers,
and release verification did not reject duplicate or invalid automatic module
names or ensure the Gradle plugin JAR carried its declared name.

## Decision

- Resolve every extension list once. Lower order is outermost for run, station
  and executor wrappers.
- Treat lifecycle hooks as brackets: start callbacks run from high to low and
  terminal callbacks from low to high. Attempt every run start callback before
  propagating the first critical start failure, so starts and completions stay
  paired.
- Reserve `TERMINAL_OBSERVER_ORDER` for built-in final-state observers and
  `PERSISTENCE_ORDER` for persistence. Micrometer records normalized terminal
  outcomes immediately before persistence.
- Measure a run from before start hooks until root-result finalization. Exclude
  ordinary completion hooks and event drain; advance the end timestamp when a
  critical completion hook changes the final outcome.
- Preserve `SKIPPED` whenever a skip condition fires. Fallback output and unary
  pass-through are continuation values, not successful operator execution.
- Catch and normalize `Exception` only. JVM `Error` values escape unchanged;
  `StationExecutionException.unwrap` therefore accepts `Exception` rather than
  `Throwable`.
- Keep sequential container order and sibling-condition semantics explicit.
  Parallel timeout/fail-fast cancels pending futures cooperatively, preserves
  declaration-ordered results and never shuts down a caller-owned executor.
- Keep framework cancellation checkpoints at execution boundaries. User code
  polls the run token only while it owns a long loop, blocking call, retry or
  other interval in which Gear4J cannot regain control.
- Freeze the documented 1.x artifact ids, stability-marked packages and distinct
  `Automatic-Module-Name` values. JPMS descriptors remain post-1.0.

## Consequences

Extension ordering is consistent across every wrapper family, and lifecycle
start/completion has a deterministic nesting model. A critical application hook
can no longer leave Micrometer reporting a successful terminal outcome that the
engine and persistence report as failed. The 1.0 exception API now makes the
fatal boundary explicit, which is an intentional pre-baseline source/binary
change for callers of `StationExecutionException.unwrap(Throwable)`.

Timeouts and cancellation remain bounded waiting contracts, not promises to
kill arbitrary in-process Java code. Advanced cancellation supervision, JPMS
descriptors, durable event replay and strict dependency locking remain separate
post-1.0 work.
