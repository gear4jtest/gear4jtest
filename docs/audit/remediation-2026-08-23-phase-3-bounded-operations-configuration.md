# 2026 audit remediation — phase 3 bounded operations and configuration

**Date:** 23 August 2026
**Baseline:** cumulative corrected phase-2 source tree
**Scope:** keyset maintenance scans, finite per-pass budgets, bounded diagnostic
reports and schema-aware artifact-store properties

## Outcome by audit finding

| Finding | Outcome | Evidence |
| --- | --- | --- |
| F-06 — unbounded maintenance sweeps and offset degradation | Closed for built-in repositories | stable object/stage cursors, streaming reconciliation, finite object/stage/diagnostic budgets, continuation reports and matching V1 indexes |
| F-07 — store configuration accepts silent typos | Closed for built-in plugins | SPI property schema, closed MEMORY/FILESYSTEM/DATABASE vocabularies, provider/common-property separation and fallback-child validation |

## Bounded consistency checks

`ArtifactConsistencyChecker` no longer increments an integer SQL offset. It
reads objects after the exclusive `(publishedAt, id)` cursor in reverse
publication order. One default pass is limited to 10,000 objects and 1,000
reported issues. The artifact metadata cache is consequently bounded by the
same object budget.

An incomplete report has `complete() == false` and a non-null `nextCursor()`.
`check(assemblyLineId, cursor)` continues after that object. Additional issues
are counted in `issuesOmitted()` rather than retained indefinitely.

## Mutation-safe staged reconciliation

`ArtifactPublicationReconciler` no longer materializes every eligible stage
before processing. It reads `(stagedAt, stageId)` keyset pages and commits or
conditionally aborts each page immediately. Deleting processed rows cannot move
an unseen row behind an offset.

One default pass is limited to 10,000 stages and 1,000 retained failures. An
incomplete report exposes a continuation cursor. Continuation must reuse the
same cutoff so the maintenance snapshot remains meaningful. `fullyReconciled()`
now also requires the bounded pass to be complete.

The existing V1 stage-age index already matches its cursor. Because Gear4J has
not had a public release, each bundled V1 external schema was updated directly
with `idx_op_chain_all (al_id, published_at DESC, id DESC)`; no compatibility V2
migration was added.

## Schema-aware store properties

`ArtifactStorePlugin.propertySchema()` defaults to an open schema, preserving
third-party plugin behavior. The three built-in plugins declare closed schemas:

- MEMORY: `maxArtifactSizeBytes`, `maxTotalBytes`, `maxEntries`;
- FILESYSTEM: `root`, `path`, `maxArtifactSizeBytes`;
- DATABASE: datasource/table/dialect, size, spool and transaction-operation
  properties.

`DefaultArtifactStoreProvider` validates root properties against the provider
schema plus the selected backend schema. It passes only backend properties to
the plugin. Each `fallback.N.props.*` map is validated by its selected plugin.
Unknown built-in properties now fail with the unknown and supported names.

## Regression coverage added

- consistency budget exhaustion and stable continuation cursor;
- reconciliation over one-row pages while processed stages are deleted;
- continuation across a finite stage budget without skipping rows;
- JDBC object and stage cursor SQL/bind ordering;
- V1 presence of the object keyset index for every dialect;
- rejection of unknown primary, fallback, filesystem and database properties;
- validation before a database resource lookup.

## Validation in the audit environment

The changed external API and external JDBC production slices compile with the
Java 17 `jdk.compiler` module. A standalone harness using the real changed
classes reported:

```text
PHASE3_HARNESS_PASS
```

The repository Python release-tool tests pass (9/9), and every shell script
passes `bash -n`. The Gradle wrapper cannot download Gradle 9.6.1 in this
environment, so no JUnit, Spotless, Checkstyle or complete build result is
claimed. Java `-Xlint:all` reports only the pre-existing missing
`serialVersionUID` warnings on repository exception classes.

Run in the connected project repository:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*ArtifactConsistencyCheckerTest' \
  --tests '*ArtifactPublicationReconcilerTest' \
  --tests '*DefaultArtifactStoreProviderTest' \
  --tests '*FilesystemArtifactStorePluginTest'
./gradlew :gear4jtest-external-jdbc:test \
  --tests '*OperationChainObjectRepositoryJdbcTest' \
  --tests '*ExternalRepositorySqlDialectContractTest' \
  --tests '*DatabaseArtifactStorePluginTest'
./gradlew check integrationTest dependencyCheckAggregate
```

## Residual risks and next phase

- A custom repository that does not implement keyset lookup cannot be used by
  these maintenance operations until adapted.
- Third-party store schemas remain open until the plugin opts into a closed
  vocabulary.
- A cursor is an opaque continuation for one assembly line/cutoff; persistence
  and scheduling of cursors remain application responsibilities.
- Phase 4 owns deterministic SPI selection, store-type extensibility,
  repository decomposition and defensive bytecode copying (F-08, F-09, F-12,
  F-13).
