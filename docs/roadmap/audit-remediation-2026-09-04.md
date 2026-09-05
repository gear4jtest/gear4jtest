# 2026-09-04 technical-audit remediation roadmap

## Document control

| Field | Value |
| --- | --- |
| Status | Partially implemented — phases 1 to 3 code complete; connected Gradle gate pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-04 |
| Target version | Pre-1.0 stabilization |

This roadmap converts the 29 August 2026 source audit into incremental, independently reviewable changes. Product
naming and any project-wide module, package or artifact rename are outside this audit and roadmap.

## Phase plan

| Phase | Priority | Scope | Exit criteria | Status |
| --- | --- | --- | --- | --- |
| 1 — correctness and immediate security | P1 | Class-unloading-safe introspection cache; accurate persistence shutdown report; inherited and validated field injection; removal of the unused public checked exception; pgJDBC security update | Focused tests, `check`, integration dependency guard and documentation links pass | Implemented; Gradle gate pending |
| 2 — runtime and persistence hardening | P2 | Define lifecycle for process-wide default executors; verify maintenance traversal and index-removal findings against existing evidence; reject overflowing JDBC timeout conversions | Lifecycle tests, timeout boundary tests, maintenance-path review and retained SQL-plan evidence pass | Implemented; Gradle gate pending |
| 3 — API and maintainability cleanup | P2/P3 | Remove raw `Stations.MapType` usage; make `ExecutionResult` nullability explicit; remove the unused `JdbcRepositoryTransaction`; isolate and compatibility-test Eclipse JDT internal API usage | API surface review, consumer smoke tests and focused compatibility tests pass | Implemented; Gradle gate pending |
| 4 — release qualification | P2 | Broaden compiler linting; make vulnerability-feed availability explicit in release CI; run complete build, publication staging, consumer smoke test, database matrix and reproducibility checks | All release gates produce retained evidence | Planned |

Dependency locking and verification metadata remain deferred until after 1.0. The existing dependency catalog, SCA and
release checks remain mandatory in the meantime.

## Phase 1 implementation

### Class unloading

`WorkerIntrospector` now uses `ClassValue<Boolean>` instead of a process-wide map keyed strongly by `Class<?>`. This
preserves thread-safe memoization while allowing generated-class classloaders to be reclaimed.

### Persistence shutdown reporting

`PersistenceShutdownReport` now separates two different facts:

- `flushExecutorShutdownStatus` reports `CALLER_OWNED`, `TERMINATED` or `NOT_TERMINATED` for the regular asynchronous
  flush executor;
- `shutdownJdbcExecutorTerminated` reports the outcome of the always-owned shutdown-only JDBC executor.

`successful()` accepts a deliberately untouched caller-owned executor, but still rejects either an owned flush
executor that did not terminate or a shutdown-only worker that outlived the deadline.

### Generated dependency injection

`SimpleDependencyInjector` now scans the complete concrete-class hierarchy. It fails early for annotated static or
final fields and wraps reflective access failures in `InjectionException` with the declaring class and field name.

### Public API cleanup

The unused, mutable checked `AssemblyLineException` has been removed before 1.0. No source, test or documentation
referenced it.

### Integration dependency

The PostgreSQL integration-test driver is updated from `42.7.11` to `42.7.13`. pgJDBC identifies `42.7.4` through
`42.7.11` as affected by CVE-2026-54291 and recommends `42.7.12` or later. Database drivers remain excluded from
published consumer runtime variants.

## Phase 2 constraints

- Do not shut down an executor supplied by an application.
- Prefer owner-scoped lifecycle and explicit shutdown over adding another global registry.
- Preserve stable ordering with a unique tie-breaker when adding cursor pagination.
- Prove index redundancy per supported dialect before removing an index. Because this is unreleased pre-1.0 code,
  update the relevant V1 migrations directly rather than adding V2 migrations.
- Keep offset pagination for user-facing random page access where cursor semantics would change the API; target only
  unbounded traversal and maintenance paths.

## Phase 2 implementation

The shared artifact fallback executor now retires its core worker after 30 seconds without work. The shared event
reaction executor already had the same bounded-lifecycle policy; a regression assertion now protects it. Both defaults
remain library-owned daemon pools, while application-supplied executors retain their existing ownership semantics. The
single daemon scheduler used for detached-cleanup deadlines remains process-scoped: its delayed tasks must stay eligible
without requiring an engine close operation that does not exist in the current public API.

`JdbcStatementOptions` now rounds directly from `Duration` seconds and nanoseconds. It accepts
`Integer.MAX_VALUE` seconds exactly and consistently rejects larger values with `IllegalArgumentException`, without an
intermediate millisecond or rounding overflow.

The offset-scan finding required no additional code: the built-in consistency checker and publication reconciler already
use stable keyset cursors with finite per-pass budgets. Remaining offset APIs are bounded by `PageRequest.MAX_LIMIT` and
serve random page access. Likewise, the simple JDBC indexes were retained because existing connected PostgreSQL evidence
shows the optimizer selecting `idx_ar_status`; redundancy has not been proved across supported dialects. See the
[phase 2 evidence record](../audit/remediation-2026-09-04-phase-2-runtime-persistence-hardening.md).

## Phase 3 implementation

The unused raw `MapType` tokens are replaced by a generic no-token fatal-signal builder. `ExecutionResult` now exposes
JSpecify nullness metadata plus `resultOptional()`, `executionOptional()` and `errorOptional()` while preserving its
existing getters. The public outcome/field matrix and the pre-1.0 `MapType` migration are documented.

The unreferenced, weaker `JdbcRepositoryTransaction` duplicate is removed. The JDT internal imports were already
confined to the `@Internal` adapter by an architecture guard; phase 3 adds an executable compatibility test covering Java
17 records, sealed types, class-file target, class loading and error diagnostics. A separate optional JDT module remains
deferred because it would change fallback availability and published dependency behavior without reducing the contained
source-level coupling further. See the
[phase 3 evidence record](../audit/remediation-2026-09-04-phase-3-api-maintainability.md).

## Required connected-environment validation

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test --tests '*OperatorIntrospectorTest'
./gradlew :gear4jtest-external-api:test --tests '*SimpleDependencyInjectorTest'
./gradlew :gear4jtest-external-api:test --tests '*ArtifactStoreExecutorsTest'
./gradlew :gear4jtest-core:test --tests '*EventHandlingDefinitionTest'
./gradlew :gear4jtest-core:test \
  --tests '*BuilderFacadesTest' \
  --tests '*ExecutionResultTest'
./gradlew :gear4jtest-external-api:test --tests '*JdtCompilerCompatibilityTest'
./gradlew :gear4jtest-jdbc:test \
  --tests '*JdbcStatementOptionsTest' \
  --tests '*PersistenceFlushCoordinatorTargetedCoverageTest' \
  --tests '*DatabaseExecutionManagerShutdownTest'
./gradlew clean check
./gradlew integrationTest dependencyCheckAggregate
./gradlew stageMavenCentral consumerSmokeTest
```

The complete release qualification should additionally execute the database matrix and reproducible-artifact checks
documented in [Releasing](../releasing.md).

## Validation in the audit environment

- The modified production paths compile with the Java 17 `jdk.compiler` module and minimal dependency stubs.
- Standalone regression harnesses passed for the `ClassValue` cache, inherited/static field injection and both
  caller-owned and component-owned persistence shutdown reporting.
- Phase 2 production changes compile with Java 17 and `-Xlint:all`; standalone harnesses passed for actual idle worker
  retirement and the exact/overflowing JDBC timeout boundaries.
- Phase 3 core changes compile with Java 17 and `-Xlint:all`; the safe-accessor/nullability harness, Java source parser,
  public API boundary analyzer and JDT-import confinement check pass. The real JDT adapter test requires Gradle and its
  pinned dependency, which are unavailable in this environment.
- All 158 repository-local Markdown files considered by the documentation-link gate have valid local links.
- The nine Python release-tool tests pass and all shell scripts pass `bash -n`.
- Gradle/JUnit, Spotless, Checkstyle, integration, SCA and publication tasks could not start because the wrapper needs
  `gradle-9.6.1-bin.zip` from an endpoint unavailable in this environment. They remain mandatory before merge.
