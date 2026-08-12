# 0038 - Crash recovery separates durable stages from temporary spool

## Status

Accepted - 2026-08-12

## Context

Gear4J uses local temporary files for bounded streaming and asynchronous copy
work, while external assembly-line publication uses an invisible metadata stage
before writing content. Both mechanisms can leave state after an abrupt process
termination, but they have different information and durability guarantees.

A spool `.tmp` file contains bytes only. It does not identify the destination,
write mode, artifact hash expected by a caller or publication operation. Replaying
such a file would therefore guess intent and could send data to a reconfigured
backend. A JDBC publication stage contains the object identity, content hash,
tags, store-configuration fingerprint, age and revision required for safe
reconciliation.

## Decision

- Keep `ManagedArtifactSpool` as private, quota-bounded temporary workspace. On
  initialization it accounts for recent residues and deletes stale residues; it
  never replays a file.
- Define spool crash loss explicitly. A synchronous database-store call has no
  acknowledged success before its database write returns. A successful composite
  `ASYNC_FALLBACKS` call acknowledges the primary only, so a crash can lose every
  fallback copy that has not completed.
- Keep the default 24-hour stale age as cleanup retention. It is not a delivery
  window and does not reduce the number of unfinished asynchronous copies that a
  crash can lose.
- Recover external publication from the durable metadata stage. After the grace
  period, reconciliation commits a stage when its expected hash exists and
  conditionally aborts an unchanged stage when the hash is absent.
- Treat JDBC commit as atomic. A transaction either exposes object metadata and
  tags and removes the stage, or retains the invisible stage for a later,
  idempotent reconciliation pass.
- Require restart-oriented tests to reconstruct repository and store objects.
  H2 provides fast feedback; the PostgreSQL, MySQL, MariaDB and Oracle matrix
  qualifies the same two stage/store crash windows.

## Crash-window guarantees

| Window | Durable fact after restart | Resolution |
| --- | --- | --- |
| Streaming spool before primary persistence | At most an unlabelled local `.tmp` residue; no acknowledged write | Count it against quota, then delete it after the stale age |
| Primary success before asynchronous fallback completion | Primary content only; any unfinished fallback copy can be lost | Accept primary-only RPO, select `SYNC_ALL`, or use durable external replication |
| Publication stage before external store write | Invisible metadata stage; expected hash absent | Conditionally abort after the grace period |
| External store write before metadata commit | Invisible metadata stage; expected hash present | Commit object metadata and tags |
| JDBC metadata commit interrupted | Either committed metadata or the original stage | Repeat reconciliation idempotently |

## Consequences

Operators can state the maximum-loss objective without confusing residue
retention with delivery durability. The local asynchronous-fallback loss bound is
all copies not completed at crash time; no time-based spool setting makes that
bound zero. The authoritative primary write remains the success boundary.

Deployments that require unacknowledged fallback work to survive a JVM crash
must use a durable replication subsystem or outbox. A more durable Gear4J spool
or JDBC outbox remains a post-1.0 option to evaluate only if that recovery
objective is required. No database schema or migration changes are introduced.
