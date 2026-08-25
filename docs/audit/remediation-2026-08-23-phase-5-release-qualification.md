# 2026 audit remediation — phase 5 release qualification

**Date:** 23 August 2026
**Baseline:** cumulative corrected phase-4 source tree
**Scope:** deterministic asynchronous tests, compiler contract documentation,
bounded operational signals and final pre-1.0 audit qualification

## Executive outcome

The five implementation phases of the 21–22 August audit are complete for the
agreed pre-1.0 scope. F-14 and F-15 are closed. The phase also finishes the
observability follow-up attached to F-03, F-04 and F-06 without introducing
pipeline identifiers or another unbounded metric dimension.

The residual technical risk is **Medium**, down from **High** in the original
audit. That rating is driven by release evidence, not by a newly identified
P0/P1 defect: the connected Gradle, JUnit, Testcontainers, SCA, formatting and
style gates could not run in this environment. A release must remain blocked
until those authoritative gates pass in the project CI environment.

Dependency locking and Gradle verification metadata are deliberately absent.
The established project policy defers them until after 1.0; this accepted F-11
risk is not a missing phase-5 deliverable. Likewise, the unreleased external
database schema was corrected in V1 and no V2 migration was introduced.

## Outcome by audit finding

| Finding | Final state | Closing phase or accepted decision |
| --- | --- | --- |
| F-01 — vulnerable integration JDBC coordinates | Closed | Phase 1 driver upgrades; connected SCA replay still required |
| F-02 — non-atomic store resolution | Closed | Phase 1 atomic identity, retained by phase 2 registry |
| F-03 — incomplete store lifecycle/cache | Closed | Phase 2 ownership; phase 5 occupancy/churn/release signals |
| F-04 — instance-local spool quota | Closed | Phase 2 directory ownership; phase 5 active-instance signal |
| F-05 — incomplete `SQLException` classification | Closed | Phase 1 complete cause/next-exception traversal |
| F-06 — unbounded OFFSET maintenance sweeps | Closed | Phase 3 keyset cursors, total budgets and truncation reports |
| F-07 — open/typo-prone built-in store properties | Closed | Phase 3 closed built-in property schemas |
| F-08 — classpath-order SPI selection | Closed | Phase 4 stable ids, ambiguity rejection and explicit selection |
| F-09 — closed `StoreType` versus open SPI | Closed before 1.0 | Phase 4 validated open value object and V1 schema |
| F-10 — unbounded MEMORY store | Closed | Phase 2 finite byte/entry limits |
| F-11 — dependency locking/verification absent | Accepted and deferred | Explicitly post-1.0 by project policy |
| F-12 — multi-responsibility JDBC repository | Closed incrementally | Phase 4 package-private collaborators, unchanged facade |
| F-13 — mutable classloader byte arrays | Closed | Phase 4 validate/copy-before-publish |
| F-14 — nine wall-clock sleeps in tests | Closed | Phase 5 causal barriers or monotonic bounded observation |
| F-15 — contradictory compiler Javadoc | Closed | Phase 5 contract aligned with javac-first/JDT-fallback behavior |
| F-16 — missing multi-instance regressions | Closed for audited invariants | Phases 1–3 deterministic resolver/spool/sweeper cases |

## F-14 — deterministic asynchronous tests

The nine `Thread.sleep(...)` calls cited by the audit have been removed. A
complete test-tree scan also found and removed four `TimeUnit.sleep(...)`
polls and one `LockSupport.parkNanos(...)` assertion helper that were not
listed in the original finding. The only remaining explicit sleep is production
backoff in `PersistenceFlushCoordinator`; it is not test synchronization.

The replacements are tied to observable causality:

- `AssemblyLineEngineWorkerConcurrencyDefaultTest` holds the first protected
  invocation until both engines have resolved the same worker instance;
- `EventManagerFailureTest` and `EventManagerTargetedCoverageTest` use
  synchronous drain completion as the event-accounting barrier;
- `EventManagerTest` records both accepted executor submissions and waits for
  owned `shutdownNow()`, which occurs after pending reactions are marked
  dropped;
- `AssemblyLineDetachAndDrainIT` submits a barrier to the shared single-thread
  reaction executor after releasing the reaction; cleanup completion precedes
  that barrier;
- `Gear4jAutoConfigurationTest.SlowOperator` blocks on an interruptible latch,
  so timeout cancellation, rather than a selected 250 ms delay, ends the work;
- compiler, loader and persistence helpers that observe private counters use a
  monotonic two-second deadline, preserve interruption and yield to the worker
  instead of sleeping for a guessed interval.

These changes preserve the existing global JUnit timeouts and do not alter
production timeout semantics.

## F-15 — generated compiler contract

`GeneratedSourceCompiler` now states the behavior implemented by
`GeneratedSourceCompilers.defaultCompiler()` and documented in the module
README: use `javax.tools.JavaCompiler` when `jdk.compiler` is present, otherwise
fall back to Eclipse JDT. It also documents that a custom `jlink` image relying
on javac must include `jdk.compiler`.

No compiler fallback is attempted after a javac source error; only backend
availability is selected at manager construction.

## Operational observability qualification

The original audit did not identify an autonomous observability defect of
medium or higher severity. It did require the resource fixes to become
diagnosable after F-03, F-04 and F-06. Phase 5 completes that follow-up:

- `AssemblyLineManager.storeResolutionStats()` exposes resolver requests,
  hits/misses, installations, configuration replacements, LRU evictions,
  explicit invalidations, final provider-lease releases, bounded occupancy,
  capacity, retained store identities and shutdown state;
- `GeneratedLoadingMetricsBinder` publishes those values under
  `gear4j.artifacts.store.resolver.*`; the only tag is the closed
  `result=hit|miss` set;
- `ArtifactSpoolStats.activeInstances()` and
  `gear4j.artifacts.spool.instances` expose JVM-local instances sharing one
  directory-scoped quota. The lock contract still prevents a second process
  from sharing that directory concurrently;
- phase-3 reconciliation/consistency reports already expose completion,
  continuation cursor and omitted issue/failure counts. They remain structured
  application-level maintenance signals rather than high-cardinality global
  metrics.

No assembly-line id, store id, configuration value, artifact hash, exception
message or secret becomes a metric tag.

No new ADR was added. This phase applies the lifecycle ownership of ADR 0043
and the explicit extension selection of ADR 0045; it does not introduce a new
architectural choice.

## Regression coverage added or strengthened

- process-wide worker locking with two causally overlapping engine executions;
- event rejection accounting after dispatcher drain;
- cancel-pending ordering before owned-executor forced shutdown;
- detached context retention and cleanup around a reaction-executor barrier;
- Spring Boot parallel default timeout against an interruptible blocked worker;
- resolver cache hit/miss, occupancy, eviction, invalidation and lease-release
  statistics;
- spool shared-instance occupancy and its Micrometer gauge;
- bounded-cardinality resolver metrics and updated metric inventories.

## Validation performed in the audit environment

Java 17 `jdk.compiler` compiled the complete core production tree with local
SLF4J API stubs and the external-api production tree except the Eclipse JDT
adapter, whose third-party compiler classes are unavailable here. The changed
compiler contract compiled separately. The changed Micrometer binders compiled
with API-compatible metric stubs. The external-api compile emitted only nine
pre-existing missing-`serialVersionUID` warnings on exception types; the
phase-5 classes introduced no new compiler warning.

Standalone harnesses exercising the real changed production classes and the
causal synchronization designs reported:

```text
PHASE5_CORE_HARNESS_PASS
PHASE5_OBSERVABILITY_HARNESS_PASS
PHASE4_ARTIFACT_SPI_PASS
PHASE4_COMPILER_SPI_PASS
PHASE4_TRANSLATOR_SPI_PASS
PHASE4_LOADER_COPY_PASS
PHASE4_JDBC_FACADE_PASS
PHASE3_HARNESS_PASS
MEMORY_HARNESS_PASS
PROVIDER_HARNESS_PASS
SPOOL_HARNESS_PASS
```

The repository release-tool tests pass 9/9 and every shell script passes
`bash -n`. Structural checks find:

- no test `Thread.sleep`, `TimeUnit.sleep` or `LockSupport.park` call;
- no dependency lockfile, dependency-locking block or verification metadata;
- no V2 external migration;
- the pre-1.0 supply-chain policy unchanged;
- no remaining Javadoc claim that JDT is the default implementation.

The authoritative Gradle command was attempted:

```bash
./gradlew spotlessCheck check integrationTest dependencyCheckAggregate
```

The wrapper could not download Gradle 9.6.1 because
`services.gradle.org` is unreachable (`java.net.SocketException: Network is
unreachable`). No JUnit, Spotless, Checkstyle, Testcontainers,
Dependency-Check or complete Gradle build result is claimed.

## Mandatory connected release gate

Run the following from the connected project repository before merge/release:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*AssemblyLineEngineWorkerConcurrencyDefaultTest' \
  --tests '*EventManagerFailureTest' \
  --tests '*EventManagerTargetedCoverageTest' \
  --tests '*EventManagerTest' \
  --tests '*AssemblyLineDetachAndDrainIT'
./gradlew :gear4jtest-external-api:test \
  --tests '*AssemblyLineStoreResolverTest' \
  --tests '*BoundedGeneratedSourceCompilerTest' \
  --tests '*GeneratedAssemblyLineLoaderTest' \
  --tests '*GeneratedLoadingRuntimeTest' \
  --tests '*ManagedArtifactSpoolTest'
./gradlew :gear4jtest-jdbc:test \
  --tests '*DatabaseExecutionManagerTest' \
  --tests '*DatabaseExecutionManagerShutdownTest'
./gradlew :gear4jtest-micrometer:test \
  --tests '*ArtifactMetricsBinderTest' \
  --tests '*GeneratedLoadingMetricsBinderTest'
./gradlew :gear4jtest-spring-boot-starter:test \
  --tests '*Gear4jAutoConfigurationTest'
./gradlew check integrationTest dependencyCheckAggregate
```

Replay the concurrency-focused tests repeatedly on the same Linux and Windows
runners used for release qualification. Replay the complete PostgreSQL,
MySQL/MariaDB and Oracle Testcontainers matrix because this audit environment
cannot validate dialect/driver behavior.

## Final residual risks

- Release evidence is incomplete until the connected command above passes.
- F-11 remains an explicit, documented post-1.0 supply-chain control; changing
  that decision requires a separate policy change, not an incidental lockfile.
- Third-party compiler, translator, store and repository implementations remain
  responsible for their documented interruption, schema and cursor contracts.
- Pre-1.0 API/schema changes require recompilation and development database
  recreation where documented; no compatibility baseline has yet been cut.

## Final assessment

The original audit is complete and its remediation roadmap is implemented for
the agreed scope. The post-remediation engineering score is **8.4/10**,
conditional on the connected release gate. The project has strong modularity,
bounded runtime services, explicit ownership, deterministic extension
selection, multi-dialect persistence discipline, extensive tests and unusually
good operational documentation. The remaining deductions are for unexecuted
authoritative build/database/SCA evidence, the accepted pre-1.0 supply-chain
decision and normal pre-1.0 compatibility churn—not for a proven unresolved
critical defect.
