# Crash recovery qualification - phase 16

**Date:** 12 August 2026
**Scope:** local spool restart and external publication between stage and commit

## Objective

Close audit acceptance scenario 12: qualify restart after a process crash around
the local artifact spool and around external publication between durable staging
and metadata commit. Document the maximum accepted loss and startup cleanup
instead of implying that temporary files form a durable queue.

## Implemented qualification

- `ManagedArtifactSpoolTest` creates a managed file through the production API,
  abandons it as a crashed process would, reconstructs the spool, verifies that
  recent bytes remain visible and quota-accounted through an actual quota
  rejection, then ages the file and proves deletion and cleanup counters on a
  later restart.
- `OperationChainPublicationRepositoryJdbcIT` stages one present and one missing
  content hash, reconstructs configuration, publication and database-store
  objects, and proves commit/abort plus an empty idempotent second pass on H2.
- `ExternalJdbcMultiDialectIT` exercises the same reconstructed-object recovery
  path on PostgreSQL, MySQL, MariaDB and Oracle in the existing container matrix.
- ADR 0038 and the external API/JDBC runbooks now distinguish the temporary spool
  from durable publication metadata and state the recovery objective explicitly.

## Recovery and maximum-loss contract

The spool has no replay metadata and is intentionally not replayed. A crash
before a synchronous database artifact write returns has no acknowledged success.
For composite `ASYNC_FALLBACKS`, a successful return acknowledges the primary
store only; all fallback copies not completed when the JVM terminates may be
lost. Recent residues consume local quota and residues older than the configured
age are deleted at the next initialization. The default 24-hour age is cleanup
retention, not a durability interval.

External publication has a stronger contract because a durable invisible stage
precedes the store write. After restart, a present expected hash is committed and
a missing hash is conditionally aborted after the caller-selected grace period.
JDBC commit is transactional and reconciliation is idempotent.

## Scope boundary

This phase does not turn local spool files into a write-ahead log, add a JDBC
outbox or introduce a schema migration. Deployments that require queued fallback
copies to survive process termination must select an external durable replication
mechanism. A Gear4J durable spool/outbox remains a post-1.0 evaluation item only
if an application recovery objective requires it.

## Validation commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*ManagedArtifactSpoolTest'
./gradlew :gear4jtest-external-jdbc:integrationTest \
  --tests '*OperationChainPublicationRepositoryJdbcIT'
./gradlew :gear4jtest-external-jdbc:integrationTest \
  --tests '*ExternalJdbcMultiDialectIT' \
  -Pgear4jDatabaseDialect=all
./gradlew check
```

The production-dialect test requires Docker. H2 remains the fast connected
restart proof when containers are unavailable. This phase does not introduce or
require dependency lockfiles, Gradle verification metadata or other deferred
post-1.0 supply-chain controls.

## Sandbox validation status

The audit sandbox could not download the configured Gradle 9.6.1 distribution
and does not provide Docker, so the commands above remain the connected closure
gate. Offline validation completed the following checks against Java 17:

- all 820 repository Java sources parse;
- the complete core, JDBC and external JDBC production trees plus the external
  API packages traversed by this qualification compile (the available
  Jackson/SLF4J APIs were used as the offline classpath);
- the three changed tests type-check against the compiled production trees and
  minimal test-framework signatures;
- an executable harness using the real spool implementation proves restart
  occupancy, quota rejection and stale cleanup;
- documentation links, ADR identifiers, trailing whitespace and changed-file
  final newlines pass equivalent static checks.
