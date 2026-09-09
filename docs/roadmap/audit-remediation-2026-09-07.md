# 2026-09-07 audit remediation plan

Source baseline: gear4jtest-20260906-213514.zip.
Audit reference: audit-technique-gear4j-2026-09-07.pdf, findings F01–F15.

This plan refines the audit's three broad horizons into four independently
reviewable implementation phases. Only phase 1 is implemented in this delivery.
An implemented change is not a completed release qualification.

| Field | Value |
| --- | --- |
| Status | Partially implemented — phases 1–2 code complete; Gradle qualification pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-08 |


## Constraints

- Java 17; incremental corrections; no project/package/artifact renaming.
- Dependency locking and verification metadata remain deferred until after 1.0.
- No public release exists: adjust V1 migrations directly when necessary.
- Never shut down caller-owned executors, stores or data sources.
- Preserve fatal Error propagation and the best-effort event contract.
- Deliver source archives without .git or transient Gradle caches.

## Phase 1 — resource ownership and parallel failure cleanup

Findings: **F01, F02, F03**. Priority: **P1**.

Scope: core/ParallelContainerBranchExecutor and external-api/
AssemblyLineStoreResolver, AssemblyLinePublicationService,
GeneratedAssemblyLineLoader, ArtifactStoreResolutionStats, their tests and the
provider ownership ADR 0043.

- Balance every provider acquisition, even when different cache entries share
  one store instance or a replacement returns the same instance.
- Give each loading/publication operation a closeable store lease. Eviction,
  replacement and resolver shutdown release cache ownership, while active
  borrowers retain the store until their operation and streams finish.
- Cancel outstanding parallel futures if predicates, task construction,
  submission, collection or failure normalization exit unexpectedly.
- Preserve the initial exception/Error, attach cleanup failures as suppressed,
  and leave caller executors running.

Exit criteria: deterministic regressions for provider/cache composition,
eviction during I/O, success/failure lease cleanup, concurrent initialization,
predicate failure, fatal branch failure and cleanup failure. Run focused core
and external-api tests, formatting and the full check task when possible.

Commit:

    fix(runtime): balance artifact store leases and cancel orphaned branches

## Phase 2 — atomic publication and consumer integrations

Findings: **F09, F12, F13**. Priority: **P1**.

Scope: external-jdbc publication repository, spring-boot-starter/
Gear4jAutoConfiguration and gradle-xml2java/XmlAssemblyLineGenerateTask plus its
plugin task wiring. This phase completes the urgent findings; it is not optional
release polish. Start after the phase 1 resource invariants are qualified.

- Serialize stage consumption against renewal/abort, preserving idempotent
  commit and all accepted tags; qualify the interleaving on real databases.
- Order or split Boot auto-configurations around datasource, transaction manager
  and actual registry producers; test real Boot consumers without manual beans.
- Declare and use the Gradle operator classpath. Decide how same-project
  operators are available before generation without a compile task cycle.
- Add TestKit clean-build and classpath invalidation tests.

Exit criteria: stage/renew/commit/abort regressions on PostgreSQL and supported
release dialects, real Boot context startup, and a consumer XML pipeline with a
business operator completing clean compileJava.

Commit:

    fix(integration): serialize publication and qualify Boot and Gradle consumers

## Phase 3 — cancellation, results and failure propagation

Findings: **F04, F05, F06, F10, F11, F14**. Priority: **P2**, with F11 grouped
alongside the JDBC cleanup work despite its lower individual severity.

- Observe cancellation during parallel/side-compute waits with one deadline.
- Accept successful null/Void operator results independently from presence.
- Start event-runtime cleanup immediately after resource acquisition.
- Roll back owned migration transactions on fatal failures; preserve cleanup
  causes and avoid implicit commits on uncertain connections.
- Close statements when timeout configuration fails.
- Keep original run failures reachable when lifecycle hooks also fail.

Exit criteria: outcome/trace consistency, bounded cancellation observation,
active runtime counts restored, JDBC fault injection and real transactional DDL
checks, complete cause chains, and caller ownership preserved.

Commit:

    fix(core): preserve cancellation, nullable results and failure causes

## Phase 4 — diagnostics, contention and release qualification

Findings: **F07, F08, F15**. Priority: **P2/P3**.

- Define log confidentiality separately from persistence capture redaction.
  Move F07 earlier if an application carries sensitive exception messages.
- Move backend construction/closure outside global monitors using per-key
  coordination, while preserving the phase 1 lease invariants.
- Reconcile documented removals with the actual source tree and archive.
- Measure large trace volumes, classloader/store churn and slow-DB behavior.
- Execute and retain JUnit, JaCoCo, JMH, SCA, database plan evidence, consumer
  smoke tests, compatibility checks, staging and reproducible builds.

Exit criteria: all remaining regressions and actual connected release gates
pass with retained evidence. No release-ready claim based only on source review.

Commit:

    chore(release): harden diagnostics and complete audit qualification

## Status

| Phase | Implementation | Connected validation |
| --- | --- | --- |
| 1 | Implemented; 17 new JUnit regressions | Gradle blocked before build startup; 15 local probe scenarios pass |
| 2 | Planned | Pending |
| 3 | Planned | Pending |
| 4 | Planned | Pending |

The earlier remediation roadmap remains historical evidence for its own audit.
Finding identifiers in this document refer exclusively to the 7 September audit.

See [phase 1 evidence](../audit/remediation-2026-09-07-phase-1-resource-lifecycle.md)
for exact changes, commands, observed results and remaining qualification gates.
