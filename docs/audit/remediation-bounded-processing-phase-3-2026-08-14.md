# Phase 3 — Bounded iteration, detached cleanup and artifact replication

**Date:** 2026-08-14

**Scope:** `IteratorStationStrategy`, `DetachedEventRuntimeCleanupScheduler`,
`CompositeArtifactStore` and their directly related public contracts/tests.

## Confirmed findings

### Iterator traversal was unbounded and accumulated twice

`IteratorStationStrategy` consumed any `Iterable` until exhaustion. An infinite
or unexpectedly large source could therefore retain the execution indefinitely
and grow the result list without a framework limit. Collector and accumulator
paths first built an intermediate `ArrayList`, then copied or collected that
list a second time. Cancellation was only observed indirectly when a child
station re-entered the runner; the strategy did not check before asking the
source for its next item.

### Completed detached runs retained their timeout task

`DetachedEventRuntimeCleanupScheduler` used
`CompletableFuture.delayedExecutor(...)`. The timeout task could not be
cancelled after normal completion, retained its cleanup closure until the full
delay elapsed and ultimately executed through JVM-global CompletableFuture
infrastructure. Cleanup `RuntimeException` values also had no explicit log.

### Asynchronous artifact replication amplified work by fallback count

`CompositeArtifactStore` created one executor task and one content copy per
fallback. The byte-array path retained one heap array per queued fallback and
did not account those copies against the managed spool quota. The default
executor used `CallerRunsPolicy`, so saturation could run fallback storage I/O
inside the supposedly asynchronous caller path after the primary write.

## Implemented corrections

- `IteratorStation` now defaults to `DEFAULT_MAX_ITEMS = 100_000`, exposes
  `maxItems(...)`, and keeps `UNLIMITED_ITEMS` as an explicit trusted-source
  opt-out.
- `IteratorStationStrategy` validates the iterable source, checks cancellation
  before requesting each item, fails with station context when the limit is
  exceeded, feeds collectors directly, writes directly to configured
  accumulators and retains only the first collected failure needed for the
  parent outcome.
- Detached cleanup uses one dedicated daemon `ScheduledThreadPoolExecutor` with
  remove-on-cancel enabled. Normal completion cancels the pending timeout,
  cleanup remains exactly-once under races, the cleanup closure is released as
  soon as either path claims it, scheduling rejection triggers immediate
  cleanup and cleanup failures are logged.
- All asynchronous fallbacks for one artifact share one quota-accounted spool
  file and one executor task. A failed fallback does not prevent subsequent
  fallbacks from being attempted, and the file is removed after execution or
  rejection.
- The default artifact executor uses a bounded queue with `AbortPolicy` and is
  exposed as a non-shutdown-capable `Executor` view. Saturation remains
  best-effort rejection after primary success and no longer becomes hidden
  caller-thread I/O.
- The phase 2 suppressed-exception assertion now narrows its element with
  `InstanceOfAssertFactories.THROWABLE` before calling `hasMessage(...)`, which
  matches AssertJ's actual generic assertion type.

## Regression coverage

- iterator limit exceeded after exactly the configured number of child runs;
- cancellation observed between two iterator items;
- cancellation remains terminal when an earlier item failure was collected;
- null iterable source reports the owning station;
- typed builder transitions preserve `maxItems` and reject invalid limits;
- early detached completion removes a one-hour timeout immediately;
- timeout/completion race runs cleanup once;
- timeout scheduling rejection cleans immediately;
- two asynchronous fallbacks share one task and one spool file;
- rejected asynchronous scheduling preserves primary success and removes spool
  state;
- phase 2 registration/cleanup failure assertion compiles with the explicit
  throwable factory.

## Validation performed in the audit environment

- all `gear4jtest-core` production sources compiled with Java 17;
- the complete external artifact package and generated-loading runtime compiled
  with Java 17;
- the five targeted JUnit source files compiled against minimal API-compatible
  JUnit/AssertJ stubs, including the AssertJ throwable narrowing;
- executable harnesses passed for iterator limit/cancellation, detached timeout
  cancellation and one-copy/two-fallback artifact replication.

The Gradle wrapper could not download Gradle 9.6.1 because this environment has
no route to `services.gradle.org`. Run the repository formatter and authoritative
test suite in the normal development environment:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*IteratorStationStrategyTest' \
  --tests '*DetachedEventRuntimeCleanupSchedulerTest' \
  --tests '*TypedBuilderIsolationTest'
./gradlew :gear4jtest-external-api:test \
  --tests '*GeneratedLoadingRuntimeTest' \
  --tests '*CompositeArtifactStoreTest'
./gradlew check
```

## Explicit non-goals

This phase does not introduce durable artifact replication or an outbox, a new
cancellation kernel, dependency locking or Gradle verification metadata. Those
larger capabilities remain post-1.0 decisions. The 100,000-item iterator default
is an intentional pre-1.0 safety change; workloads above it must select an
explicit application bound or the trusted `UNLIMITED_ITEMS` opt-out.
