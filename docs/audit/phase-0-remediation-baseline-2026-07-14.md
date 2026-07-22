# Phase 0 — Remediation baseline revalidation

**Date:** 14 July 2026
**Source archive:** `gear4jtest-20260714-115024(1).zip`
**Reference audit:** `audit-technique-gear4j-2026-07-13(1).md`
**Purpose:** verify the audit findings against the latest supplied sources before functional remediation.

## Outcome

The audit remains materially valid on the latest sources.

- **27 findings are still present.**
- **1 finding is partially obsolete:** `jreleaser.yml` is now present, while `LICENSE`, `NOTICE`, the release dry-run gate and related release completeness checks remain missing.
- **1 low-priority finding is no longer applicable:** the audited `InMemoryExternalConfigCache` implementation is absent from the latest sources, and no equivalent `loader.get()` / `putIfAbsent` miss path was found in the experimental cache module.
- **No runtime P0/P1 finding was disproved.**

The remediation order defined after the audit remains appropriate. The release phase must be adjusted so that it does not recreate `jreleaser.yml`; it must validate the current file and complete the missing legal and CI assets instead.

## Dynamic validation status

The repository contains a complete Gradle wrapper targeting Gradle 9.6.1 with a pinned distribution checksum. The build could not be started in the audit execution environment because the distribution was not cached and the environment could not resolve `services.gradle.org`:

```text
Downloading https://services.gradle.org/distributions/gradle-9.6.1-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Consequences:

- `check`, integration tests, JaCoCo, TestKit and JReleaser validation were not executed in this phase;
- source findings were revalidated directly against the latest files;
- autonomous reproductions not requiring project dependencies were executed where possible;
- no unexecuted test was added to the repository merely to create a nominal phase-0 change.

## Confirmed autonomous reproduction: UUIDv7 clock rollback

`DefaultUuidGenerator` was compiled in isolation with `javac --release 17`. Its thread-local state was placed 1.2 seconds ahead of wall-clock time with the 12-bit sequence exhausted, reproducing a clock rollback/VM snapshot scenario.

Observed result:

```text
configuredRollbackMs=1200
generateElapsedMs=1190
wall=1.31 cpu=97%
```

This confirms that `DefaultUuidGenerator.generate()` actively spins for approximately the complete clock rollback duration after sequence exhaustion. The finding is therefore not merely theoretical.

Phase 4 closes this historical reproduction by replacing clock polling with a
bounded logical-timestamp advance. The measurement above describes the
pre-phase-4 implementation retained as audit evidence.

## Revalidated finding matrix

| ID | Audit finding | Current status | Current evidence | Remediation phase |
|---|---|---|---|---:|
| A01 | Public API depends on packages marked internal | **Closed in phase 10** | public executor, trace, concurrency, context lookup and persistence-monitoring contracts now live in stable packages; `ApiBoundarySourceTest` requires zero core API/SPI dependencies on engine/execution/internal packages. | Closed |
| A02 | Provider-neutral `external-api` contains duplicated JDBC code | **Closed in phase 10** | the residual JDBC stream and duplicated metrics class were removed; the architecture test now rejects `java.sql`/`javax.sql` imports in `external-api`. | Closed |
| A03 | Publication atomicity depends implicitly on repository capability | **Closed in phase 8** | `AssemblyLineManager` now requires `OperationChainPublicationRepository` at build time; the sequential object-then-tags fallback has been removed and an atomic in-memory implementation is available. | 8 |
| A04 | Generated compiler fallback/classloader semantics are inconsistent | **Confirmed** | Any javac `CompilationException` still triggers JDT; javac classpath still comes only from `java.class.path`; JDT still uses internal Eclipse compiler APIs with release mode disabled. | 11 |
| A05 | Parallel cancellation can replace an already completed result | **Fixed in phase 3** | all timeout/interruption/cancellation paths now resolve completed futures before cancellation and retry resolution when `cancel(true)` loses the completion race; a deterministic regression test covers the exact window. | Closed |
| A06 | UUIDv7 can spin after clock rollback | **Fixed in phase 4** | the spin loop was removed; sequence exhaustion advances a thread-local logical timestamp, and deterministic frozen/rolled-back clock tests prove one clock read per generated UUID. | Closed |
| A07 | Java domain objects do not enforce database/runtime invariants | **Closed in phases 9 and 10** | `OperationChainObject` and `OperationChainConfig` enforce the V1 database constraints, while `FlowConfig` now rejects null failure, stop and cancellation policies at construction. | Closed |
| A08 | Some invalid parallel configurations fail only during execution | **Fixed in phase 3** | container construction now rejects sibling conditions in parallel mode, missing parallel executors and non-positive station await timeouts; the runtime compatibility check remains as defense in depth. | Closed |
| A09 | Execution-context ID collision silently replaces an active context | **Fixed in phase 3** | registration now uses `putIfAbsent` and fails on an active duplicate; run cleanup removes only `(executionId, expectedContext)`, with registry and cleanup regression tests. | Closed |
| A10 | External API error taxonomy is unstable | **Closed in phase 10** | external failures now share `Gear4jExternalException` and stable `ExternalErrorCode` categories; lookup and configuration misses use typed NOT_FOUND exceptions and compilation/repository/policy failures preserve their code. | Closed |
| A11 | `external-jdbc` is not covered by the advertised multi-dialect matrix | **Closed in phase 7** | `ExternalJdbcMultiDialectIT` covers migrations/checksums, config JSON, atomic publication/rollback, tags, pagination and streaming BLOBs on PostgreSQL, MySQL, MariaDB and Oracle; `all` is the local default, pull requests require all four matrix jobs, and `releaseCheck` refuses a single-dialect override. | 7 |
| A12 | Coverage gate protects only four classes | **Confirmed** | `critical-coverage-thresholds.json` still contains exactly four class thresholds and no module/global ratchet. | 12 |
| A13 | Gradle plugin lacks TestKit/configuration-cache execution tests | **Confirmed** | plugin tests still use `ProjectBuilder`; CI executes configuration-cache checks only on `help`; task action still calls `project.delete`. | 11 |
| A14 | Spring Boot persistence is unredacted by default | **Fixed in phase 2** | default is now `RedactionMode.DISCARD`; without a bean, the starter supplies `SensitiveDataRedactor.discardSensitiveValues()`. Raw capture requires `DISABLED`, deprecated explicit `WARN`, or an explicit no-op bean. | Closed |
| A15 | XML size limit is enforced after schema validation | **Closed in phase 9** | `AssemblyLineValidator` now bounds byte arrays and streams before XSD validation; the stream path reads at most `maxXmlBytes + 1` bytes before rejection. | Closed |
| A16 | Dependency locking and cryptographic verification are optional in practice | **Confirmed** | no lockfile or `verification-metadata.xml` exists; CI skips strict verification when absent; release does not pass `gear4j.enforceSupplyChain=true`; CVSS threshold remains 9.0. | 12 |
| A17 | Global persistence monitor is held during JDBC I/O | **Fixed in phase 5** | `PersistenceOperationGate` now locks only admission/counter transitions; repository writes and per-buffer flush/finalization execute outside the lifecycle lock. Deterministic tests prove two independent `start` writes can enter the repository concurrently and shutdown closes admission before waiting for an admitted write. | Closed |
| A18 | Immutable generated content can be compiled repeatedly | **Confirmed** | publication validation and generated-line loading still call the compiler separately; loader single-flight does not reuse publication validation bytecode. | 11 |
| A19 | Experimental cache duplicates concurrent loads on miss | **No longer applicable** | audited `InMemoryExternalConfigCache` is absent; no equivalent loader miss path was found under `gear4jtest-experimental-cache/src/main`. | Closed |
| A20 | Persistence shutdown timeout is not a real end-to-end bound | **Closed in phase 6** | one monotonic deadline now starts before admission closure and bounds operation waits, buffer locks, retries, backoff and shutdown JDBC-worker waits. Uncooperative JDBC may outlive the immutable report only on a daemon worker, with retained data and non-termination reported. | 6 |
| A21 | Artifact is stored before validation/metadata with no orphan cleanup capability | **Mitigated in phase 9** | new content is translated and compiled before storage; durable staged metadata precedes `store.put`, and `ArtifactPublicationReconciler` commits or abandons stale stages. The generic store SPI still cannot enumerate legacy store-only artifacts. | Closed for manager publications; legacy reverse-orphan enumeration remains future work |
| A22 | Connection acquisition is outside JDBC statement timeout | **Confirmed for normal operations; shutdown caller mitigated in phase 6** | repositories still obtain `DataSource#getConnection()` before statement timeout configuration. Shutdown now bounds its caller through daemon workers, but pool acquisition limits for ordinary writes remain an operational responsibility. | 12 |
| A23 | Latest-RUN index does not fully match filter/order | **Confirmed structurally** | query orders by `published_at DESC, id DESC`; indexes omit `id`, and non-PostgreSQL indexes also omit `mode`. Actual cost still requires dialect-specific `EXPLAIN`. | 7 |
| A24 | Artifact-store provider accepts malformed configuration silently | **Closed in phase 9** | booleans are strict, fallback groups and indices are validated, replication/self-healing without fallback are rejected, and `availableTypes()` returns an immutable copy. | Closed |
| A25 | JDBC/Testcontainers test dependencies are injected into unrelated modules | **Confirmed** | root `build.gradle` still adds all drivers and database Testcontainers dependencies to every non-Gradle subproject. | 12 |
| A26 | Release assets/configuration are incomplete | **Closed in phase 1** | legal files are present and packaged, JReleaser is isolated and validated, staged JAR/POM metadata is checked, and the CI/release workflows enforce the release assets. | Closed |
| A27 | Java 17 compatibility relies on build JDK instead of `--release` | **Confirmed** | main modules still use `sourceCompatibility`/`targetCompatibility` only; no project Java 17 toolchain or `options.release = 17` is configured. | 12 |
| A28 | Artifact reproducibility is not demonstrated | **Confirmed** | manifest still embeds current Java vendor/version; archive reproducibility flags and double-build hash comparison are absent. | 12 |
| A29 | Documentation contains claims/links not supported by delivered sources | **Closed for audited examples in phase 1** | stale external-JDBC links and the overstated orphan-detection claim were corrected, and local Markdown links are now checked by the build. | Closed |

## P0/P1 remediation gates

The following findings must be treated as release blockers before broad pre-1.0 stabilization:

1. **A26 / A29 — release and documentation completeness.**
2. **A14 — safe Spring Boot redaction default.**
3. **A05 — exact parallel cancellation outcome.**
4. **A06 — bounded UUIDv7 generation under clock rollback.**
5. **A17 — no global monitor across JDBC I/O.**
6. **A20 / A22 — bounded shutdown and connection-acquisition contract.**
7. **A11 — real `external-jdbc` multi-dialect coverage.**
8. **A03 — mandatory atomic publication capability.**
9. **A13 — real Gradle TestKit/configuration-cache validation.**

## Test gaps to add with the corresponding fixes

Tests for unresolved behavior should be committed with the phase that changes the behavior, rather than as intentionally failing tests in phase 0.

- Phase 2: persistence enabled without a redactor must prove that raw values are not persisted by default.
- Phase 3: deterministic completed-but-not-collected cancellation race; registry collision and expected-context cleanup; build-time invalid parallel configuration.
- Phase 4: injected frozen/rolled-back clock, sequence exhaustion, latency bound and UUID invariants.
- Phase 5: two independent slow persistence operations must overlap rather than serialize globally.
- Phase 6: wall-clock shutdown bounds for an occupied buffer lock, exhausted connection pool and slow statement.
- Phase 7: external schema, transaction rollback, tags, BLOB and latest-RUN behavior on PostgreSQL, MySQL, MariaDB and Oracle.
- Phase 8: Nth-tag failure must roll back the complete publication and permit idempotent retry.
- Phase 9: failed compilation/metadata publication must clean or retain a reconcilable staged artifact; malformed store configuration must fail fast; XML must reject oversize input before XSD work.
- Phase 11: javac parent-only dependency, syntax failure without JDT retry, and Gradle TestKit cache reuse.
- Phase 12: supply-chain files mandatory on release, Java 17 consumer check, reproducible double build and expanded coverage ratchets.

## Phase 0 exit decision

**Phase 0 is complete from a source-revalidation standpoint.** No production code has been changed. The audit roadmap remains valid with these two adjustments:

- phase 1 validates and completes the already-present JReleaser configuration instead of creating it;
- the former experimental-cache single-flight item is removed from the remediation backlog unless a future cache loader reintroduces the same pattern.

A connected Java 17 environment must still execute the standard validation sequence before any remediation branch is merged:

```bash
./gradlew --no-daemon spotlessCheck
./gradlew --no-daemon check
./gradlew --no-daemon releaseCheck -Pgear4j.enforceSupplyChain=true
JRELEASER_DRY_RUN=true ./gradlew --no-daemon jreleaserDeploy -PprojectVersion=1.0.0-rc1
```
