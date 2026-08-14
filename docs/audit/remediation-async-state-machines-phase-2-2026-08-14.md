# Asynchronous state-machine remediation — phase 2

**Date:** 14 August 2026

**Scope:** generated loading single-flight and periodic JDBC flush scheduling

## Generated loading cleanup

`GeneratedLoadingRuntime` keeps a timed-out or failed registration flight until
the staged classloader entry has been discarded. A custom
`ClassLoaderRegistry.evictIfOwned(...)` failure previously skipped both the
cleanup-state transition and removal of the in-flight key. Every later request
then joined the already-failed future and retry was impossible for the lifetime
of the manager.

Registration cleanup is now completed in a `finally` block. Cleanup failures are
attached to the primary registration failure when one exists, logged without
payload data, and cannot retain the single-flight key. The failed registration
lease remains unpublished, so a stale custom-registry entry is never exposed as
a valid generated assembly line.

The failure-completion path also completes its future and releases non-registering
flights even if best-effort task cancellation itself fails. An internal loading
operation returning no result while its flight is still active now fails
immediately instead of occupying the slot until timeout.

## Periodic JDBC flush continuity

`ScheduledExecutorService.scheduleWithFixedDelay(...)` suppresses every later
execution when one invocation terminates exceptionally. A bounded flush-executor
rejection escaped `flushPendingBuffersSafely()`, so a single saturation event
could permanently disable low-volume periodic persistence for all runs.

The maintenance pass now isolates the existing
`ExecutionPersistenceException` per buffer. Saturation is still recorded and
surfaced as a persistence failure; it is not silently downgraded. The scheduled
maintenance task nevertheless remains alive and continues examining other and
future run buffers.

## Regression coverage

- registration failure combined with cleanup failure releases the in-flight key,
  retains both diagnostics and permits a retry;
- a periodic scheduling rejection does not escape the maintenance callback;
- a buffer created after that rejection is flushed by a later maintenance pass.

## Disconnected validation

The affected production sources and both focused JUnit classes compile with the
Java 17 compiler. Executable harnesses using the real state-machine and buffer
implementations reported:

```text
generated-loading-phase-2-smoke: OK
persistence-flush-phase-2-smoke: OK
```

The Gradle wrapper distribution cannot be downloaded from this audit workspace.
The connected build remains the authoritative verification gate:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test --tests '*GeneratedLoadingRuntimeTest'
./gradlew :gear4jtest-jdbc:test --tests '*PersistenceFlushCoordinatorTargetedCoverageTest'
./gradlew check
```
