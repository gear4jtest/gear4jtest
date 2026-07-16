# Audit remediation closure matrix - 2026-07-13

## Scope and interpretation

This matrix reconciles the 47 findings from the 10 July 2026 audit with the cumulative source tree after remediation
lot 5.3, the closing lot, phase 6 and phase 7. It distinguishes implemented corrections from accepted MVP risk and
remaining operational qualification.

"Closed" means that code, tests or an explicit contract now address the finding. It does not replace the mandatory CI
run on a host able to resolve the Gradle 9.6.1 distribution and execute Testcontainers.

## Executive status

- 45 findings are closed or contractually addressed.
- 1 finding is mitigated but retains a documented structural limitation.
- 1 finding is accepted for the MVP by explicit decision.
- No finding remains deferred to phase 7; connected CI qualification and the accepted/mitigated risks remain.
- No unaccepted P0/P1 functional, concurrency, persistence, security-boundary or release-configuration correction from
  phases 0 to 5 remains open in the source tree.
- Gate D is implemented in source: modular extraction, typed contracts, compatibility tooling and documentation are present. It becomes qualified after the connected Gradle 9/Testcontainers/release dry-run succeeds.

## Finding-by-finding matrix

| Id | Finding | Status | Evidence or remaining action |
|---|---|---|---|
| F01 | Mutation of `AssemblyLine.Builder#then` | Closed | Non-mutating builder and characterization tests in `gear4jtest-core`. |
| F02 | Mutation of station builders | Closed | Work, unary, sequence and iterator builder transitions copy their source builder; regression tests cover ignored return values. |
| F03 | external-api coupled to JDBC/Jackson | Closed in phase 7 | JDBC repositories, migrations, database artifact storage and Jackson JSON usage moved to `gear4jtest-external-jdbc`; `external-api` now depends only on core, SLF4J and JDT. |
| F04 | Weakly typed public contracts | Closed in phase 7 | `RunRequest<IN>` and `GeneratedAssemblyLine<IN, OUT>` remove raw public definitions before the 1.0 baseline; XML generation emits concrete type parameters. |
| F05 | Insufficient construction invariants | Closed with compatibility exception | Required worker ids, versions, operator types, durations and capacities fail fast. Historical empty `SignalStation` facade id remains a documented pre-1.0 compatibility exception. |
| F06 | Shared mutable context values | Contractually addressed | Top-level maps are distinct and `AssemblyLineEngine.Builder.initialRunContextPolicy(...)` enables defensive value copies. Shallow values remain the compatibility default. |
| F07 | Invalid generic Jackson cloning contract | Closed | Concrete collection/map type preservation and explicit `PayloadCloneException` replace downstream `ClassCastException`. |
| F08 | Side-compute null/repeated execution | Closed | Null result/fallback rejection and once-only per-run execution are covered by tests. |
| F09 | Generic repository exceptions | Closed | Typed repository failures, update-count validation and shutdown diagnostics are used by persistence paths. |
| F10 | Large multi-responsibility classes | Closed incrementally in phase 7 | `BaselineSchemaValidator` and `EventSubscriptionResolver` were extracted; XML validation, parsing and rendering were already split into focused collaborators. No rewrite was introduced. |
| F11 | Missing generic contract tests | Closed | Builder and Jackson generic regressions are characterized. |
| F12 | Missing reentrancy/predicate tests | Closed | Guard reentrancy/release and isolated slow/failing predicates are tested. |
| F13 | Coverage without thresholds | Closed in phase 6, pending first CI calibration | `critical-coverage-thresholds.json` and `jacocoCriticalCoverageVerification` enforce branch ratchets on four critical classes from combined unit/integration data. |
| F14 | Monolithic multi-dialect suite | Closed and strengthened in phase 7 | The local default is the complete four-dialect matrix; pull-request/main/scheduled/release workflows distribute independently selected Testcontainers dialects in parallel. |
| F15 | Vulnerable Jackson 2.19.0 | Closed | Version catalog uses Jackson 2.21.5; the dependency scan remains part of `releaseCheck`. |
| F16 | Arbitrary property methods in GEL | Closed | Explicit `PropertyAccessPolicy`, inert value trees and cached safe accessors are the default boundary. |
| F17 | Implicit temporary spool | Closed | Private, bounded, observable and cleanable spool policy replaces the implicit temp path. |
| F18 | Optional supply-chain enforcement | Accepted MVP risk | Locks/checksums stay optional by explicit decision. `gear4j.enforceSupplyChain=true` is the documented opt-in fatal gate. |
| F19 | Reentrancy guard lost | Closed | Guard ownership is execution-context scoped and reentrant acquisition fails fast. |
| F20 | Predicates block shared dispatcher | Closed | Predicate plus reaction execute in the bounded reaction executor; exceptions are isolated and counted. |
| F21 | Unbounded global dispatcher queue | Closed in phase 6 | The shared drain-task queue is bounded, configurable at startup and rejects without blocking; affected run-local events are dropped and counted under the documented best-effort contract. |
| F22 | Cooperative cancellation only | Contractually addressed | Shared cancellation token is propagated and checked between managed steps/branches; non-cooperative user code remains an explicit limitation. |
| F23 | Concurrent compilation not deduplicated | Closed in this lot | `GeneratedAssemblyLineLoader` uses one future per immutable loader id, shares failures and permits retry. Alias invalidation generations prevent stale restoration. |
| F24 | BLOB fully materialized in heap | Closed | JDBC artifacts stream with configured size limits and spool fallback; large-volume tests cover the boundary. |
| F25 | Uncached GEL reflection | Closed | Safe accessor metadata is cached with `ClassValue`/method handles without a global class-retention map. |
| F26 | Incomplete schema baseline | Closed | Baseline is explicit, disabled by default and validates required V1 tables, columns and indexes. |
| F27 | Non-transactional publication | Closed | Metadata and tags publish atomically through the publication repository with idempotent conflict behavior. |
| F28 | `Instant` depends on JVM/DB timezone | Closed | JDBC conversions use UTC and tests vary timezone assumptions. |
| F29 | Shutdown flush loses retry state | Closed | Bounded retry/backoff and `PersistenceShutdownReport` preserve and expose residual records. |
| F30 | Inconsistent automatic schema creation | Closed | Auto-create and baseline remain explicit, validated and disabled by default. |
| F31 | Referential integrity across external stores | Partially mitigated | Polymorphic stores prevent one valid global FK. `ArtifactConsistencyChecker` detects metadata that references a missing artifact, but the current SPI cannot enumerate store-only artifacts and therefore cannot detect or remove reverse orphans. Durable cross-store cleanup remains future work. |
| F32 | Late configuration validation | Closed | Runtime/Spring properties validate capacity, timeout, dialect and persistence combinations before use. |
| F33 | No-op redaction outside Spring | Closed | Direct managers capture metadata only by default; raw sensitive capture requires explicit policy/redactor. |
| F34 | Incorrect Gradle dependency scopes | Closed | Public Jackson, XML/external, Spring, Micrometer and Actuator types use consumer-visible scopes and are compiled by the external fixture. |
| F35 | Missing release configuration and legal assets | Closed in phase 1, pending connected execution | Root `jreleaser.yml`, `LICENSE` and `NOTICE` are versioned. CI validates the JReleaser model and release assets; staged JAR/POM contents are checked before deployment. |
| F36 | Gradle plugin not published | Closed in this lot | Plugin implementation, sources/Javadocs and both marker publications are included in `stageMavenCentral`. |
| F37 | Surprising coverage graph/unused plugins | Closed in phase 6 | Per-subproject coverage finalizers and unused Asciidoctor application are removed; JMH is restricted to the core-hosted benchmark harness. |
| F38 | Gradle major migration pending | Closed in phase 7, pending connected CI | Wrapper and verified distribution checksum target Gradle 9.6.1, JMH plugin is updated and CI exercises a strict reusable configuration cache with all warnings. |
| F39 | Health remains permanently DOWN | Closed | Readiness uses current DB/backlog state and successful recovery; liveness stays process-local. |
| F40 | Unbounded Micrometer cardinality | Closed | Default tags omit identifiers; finite allowlist maps unknown identifiers to `other`. |
| F41 | No process-wide event aggregation | Closed in phase 7 | `EventRuntimeMetrics` aggregates queue, drop, reaction, dispatcher rejection and latency signals; the starter binds tag-free process gauges automatically. |
| F42 | Obsolete/duplicated ADR and media-type docs | Closed in phase 7 | Duplicate ADR/architecture files were removed and `application/vnd.gear4j.assembly-line+xml` is the documented canonical XML media type. |
| F43 | Incomplete `package-info` stability markers | Closed in phase 7 | Missing Spring Boot markers were added and the source-boundary test now scans every published Java library module, including external JDBC. |
| F44 | Release path not executable | Implemented and guarded, pending connected dry run | `releaseMetadataCheck` validates configuration without credentials. `releaseCheck` stages, inspects legal/POM metadata, scans and invokes the autonomous consumer; `docs/releasing.md` documents credentials and the non-publishing dry run. |
| F45 | Unbounded mutable experimental cache | Closed in phase 6 | The repository now enforces TTL, entry/weight bounds, LRU eviction, clone-on-write/read and observable hit/miss/eviction/load statistics; the API remains experimental. |
| F46 | No external consumer test | Closed in this lot | `config/consumer-smoke` resolves Gear4J coordinates and plugin markers exclusively from staging while resolving third-party dependencies from Maven Central, without project dependencies. |
| F47 | No 1.0 compatibility policy | Closed in phase 7 | `docs/compatibility-policy.md` defines Java/SPI/XML/DB/property/metric guarantees; Japicmp is mandatory for stable releases after 1.0.0. |

## Release gates

### Gate A - technical publication

The source/configuration side is implemented: legal assets, local-link validation, JReleaser model validation, staged
libraries, plugin markers, staged JAR/POM inspection, dependency scan and autonomous consumer are wired. The gate
becomes fully qualified only after `releaseCheck` and the JReleaser dry run pass in connected CI.

### Gate B - functional release candidate

The planned phase 2-4 contracts are implemented: typed builder behavior, reentrancy/event isolation, cancellation
propagation, single-flight loading, explicit baseline/UTC, atomic publication and observable shutdown retry.

### Gate C - production candidate

The phase 5 boundaries are implemented: explicit GEL access, inert inputs, sensitive-capture policy, private bounded
spool, recoverable readiness and bounded metric tags. Environment-specific probe thresholds and dashboards still need
deployment qualification.

### Gate D - stable 1.0

Implemented in source, pending qualification. Phase 6 performance/cache/quality gates and phase 7
architecture/compatibility gates require their first complete connected CI and release dry-run before 1.0.0 can be
declared qualified.

## Mandatory validation on the delivery host

```bash
./gradlew spotlessApply
./gradlew releaseMetadataCheck
./gradlew help --configuration-cache --configuration-cache-problems=fail --warning-mode=all
./gradlew clean build
./gradlew verifyPerformanceBudgets
./gradlew :gear4jtest-jdbc:integrationTest
./gradlew :gear4jtest-external-jdbc:integrationTest
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
./gradlew verifyStagedReleaseArtifacts -PprojectVersion=1.0.0-rc1
./gradlew apiCompatibilityCheck -PprojectVersion=1.0.1 -Pgear4j.apiBaselineVersion=1.0.0
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

The first release candidate must additionally run the complete Testcontainers dialect matrix and inspect the staged
POM, source, Javadoc, plugin implementation and marker artifacts before any non-dry deployment.
