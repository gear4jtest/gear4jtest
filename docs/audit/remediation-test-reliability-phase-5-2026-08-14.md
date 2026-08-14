# Phase 5 — Test reliability and missing concurrency regressions

**Date:** 2026-08-14

**Scope:** Gradle/JUnit test execution policy, asynchronous event tests,
parallel-container tests and the publication-tag boundary introduced in phase
4. Production code, public APIs and coverage thresholds are unchanged.

## Confirmed findings

### The dispatcher regression test observed counters through polling

`EventDispatcherTest.dispatchLoop_shouldContinueAfterAnUnexpectedTaskFailure()`
waited for `failedTasks` and `completedTasks` with one-millisecond sleeps. Its
final assertions were bounded, but the observation point was still scheduled
independently from the daemon dispatcher thread. This is the test that had
already failed intermittently in CI.

The dispatcher in this test has one worker. A task submitted after the tested
tasks is therefore a stronger synchronization point than time-based polling:
when that barrier starts, every earlier task has returned and its metric has
been accounted for.

### Several concurrency tests used sleep duration as their blocking primitive

`ContainerStationStrategyTest` used sleeps of 250 ms and 5 seconds to keep slow
branches alive while testing timeout, cancellation and fail-fast interruption.
`EventManagerTest.shutdown_shouldDrainAlreadyQueuedEvents()` similarly slept
for 150 ms inside the reaction. These tests depended on scheduler timing and
could pass or fail for environmental reasons unrelated to the contract being
tested.

### An accidentally blocked test had no project-wide upper bound

Many critical concurrency classes already declared `@Timeout`, but the Gradle
test convention did not define a fallback for the rest of the unit and
integration suites. A forgotten latch release or external test stall could
therefore occupy a CI worker indefinitely.

### Phase 4 lacked a concurrent regression at the persisted tag boundary

Phase 4 serialized retry tag merges with `SELECT ... FOR UPDATE` and limited
the persisted union to 64 entries. Unit coverage checked oversized input, but
there was no test where two transactions concurrently attempted to add the
64th distinct tag to the same 63-tag stage.

## Implemented corrections

- Added a JUnit Jupiter default timeout of 2 minutes per test and 5 minutes per
  lifecycle method to every Gradle `Test` task. Explicit `@Timeout`
  declarations remain more specific and authoritative.
- Extended `BuildConventionsFunctionalTest` to verify that both `test` and the
  lazily created `integrationTest` task inherit those limits.
- Replaced `EventDispatcherTest` counter polling with a blocking dispatcher
  barrier. The barrier holds its own completion increment until an immutable
  stats snapshot has been captured.
- Reworked the event-runtime drain test around a latch and a recording owned
  executor. The test now proves that executor shutdown has been initiated,
  that synchronous runtime shutdown is still waiting, and that releasing the
  reaction completes the drain.
- Replaced all four fixed-duration slow branches in
  `ContainerStationStrategyTest` with explicit latches. Timeout and fail-fast
  behavior now cause real `Future.cancel(true)` interruption rather than
  racing a chosen sleep duration.
- Added class-level 10-second limits to the event-manager and container test
  classes, and a 30-second limit to the H2 publication integration class so the
  same safety applies when individual tests are launched directly from an IDE.
- Added the missing `OperationChainPublicationTags.merge(...)` boundary test:
  a duplicate retry is valid at 64 persisted tags, while a new distinct tag is
  rejected.
- Added an H2 concurrency integration test starting from 63 tags. Two
  synchronized retry transactions add different tags; exactly one succeeds,
  the other receives the bounded-union error, and the durable stage contains
  exactly 64 tags with only one retry tag.

## Compatibility and coverage impact

There is no runtime or public API change in this phase. The only execution
policy change is test-side: a single test method that legitimately needs more
than two minutes, or a lifecycle method needing more than five minutes, must
declare an explicit `@Timeout` or a more specific JUnit timeout configuration.

No JaCoCo threshold was raised or reduced. The existing module and critical
branch ratchets stay unchanged until a connected, authoritative Gradle run can
recalibrate them from real reports.

## Regression coverage added or strengthened

- shared dispatcher recovery after an unexpected task failure;
- exact accounting after queue saturation without counter polling;
- synchronous event-runtime drain while a reaction is in flight;
- default parallel timeout and `IGNORE_AND_CONTINUE` timeout behavior;
- fail-fast sibling interruption through an actual interrupted latch wait;
- engine-level default timeout without a station override;
- tag-union semantics at exactly 64 persisted entries;
- transactional serialization of two concurrent publication-stage retries.

## Validation performed in the audit environment

- Compared the cumulative tree with the phase-4 archive: before this report,
  exactly the seven intended implementation/test files differed.
- Inspected the resulting diffs for executor ownership, interruption restoration,
  bounded waits and guaranteed latch release in `finally` blocks.
- Confirmed that the four `Thread.sleep(...)` calls targeted in
  `ContainerStationStrategyTest`, the dispatcher polling sleeps and the event
  drain sleep are removed.
- Confirmed that coverage-policy JSON files and production Java sources are
  untouched.
- Checked the deliverable inventory to exclude generated `.class`, partial
  downloads, lock files and `.git` metadata.

The Gradle wrapper could not download Gradle 9.6.1 because this environment has
no route to `services.gradle.org`. This runtime also exposes a Java 17 JRE but
not `javac`, so test-source compilation cannot honestly be claimed here. Run
the authoritative formatter and suites in the normal development environment:

```bash
./gradlew spotlessApply
./gradlew :build-logic:test \
  --tests '*BuildConventionsFunctionalTest'
./gradlew :gear4jtest-core:test \
  --tests '*EventDispatcherTest' \
  --tests '*EventManagerTest' \
  --tests '*ContainerStationStrategyTest'
./gradlew :gear4jtest-external-api:test \
  --tests '*OperationChainPublicationTagsTest'
./gradlew :gear4jtest-external-jdbc:integrationTest \
  --tests '*OperationChainPublicationRepositoryJdbcIT'
./gradlew check
```

For the concurrency-sensitive cases, also run repeated focused verification on
the CI runner used by the project:

```bash
for run in $(seq 1 25); do
  ./gradlew :gear4jtest-core:test \
    --tests '*EventDispatcherTest' \
    --tests '*EventManagerTest.shutdown_shouldDrainAlreadyQueuedEvents' \
    --tests '*ContainerStationStrategyTest' || exit 1
done
```

## Explicit non-goals and residual risks

- Two bounded polling loops remain in
  `EventManagerTest.shutdown_shouldCompleteWhenCancelModeDropsQueuedReactionBeforeStart()`.
  They observe private runtime transitions for which the current API exposes no
  deterministic signal. The new class timeout prevents an unbounded hang; a
  future test seam should replace those polls if that test still flakes.
- The new transaction race runs on H2. PostgreSQL, MySQL/MariaDB and Oracle
  locking semantics remain covered by the existing Testcontainers matrix and
  must be replayed in the connected validation phase.
- Existing sleep calls outside the targeted critical scenarios were not
  mechanically removed. Some are bounded polling helpers; others deliberately
  widen a timing window and require a dedicated synchronization design before
  replacement.
- This phase does not change dependency locking or verification metadata. Those
  supply-chain controls remain explicitly deferred until after 1.0.
