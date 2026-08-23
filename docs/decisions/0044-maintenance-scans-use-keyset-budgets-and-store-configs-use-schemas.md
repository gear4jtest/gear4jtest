# 0044 - Maintenance scans use keyset budgets and store configurations use schemas

## Status

Accepted - 2026-08-23

## Context

Artifact consistency checks paged operation-chain objects with an integer
offset and had no total object or issue budget. Publication reconciliation first
loaded every eligible stage into memory because processing a page deletes rows
and would make a following offset skip stages. Both operations could therefore
consume unbounded work or memory on a large installation.

Artifact-store plugins parsed selected values but did not declare their property
vocabulary. Unknown keys were ignored. A typo such as `maxEntry` could silently
select a default capacity instead of failing configuration.

## Decision

- Object scans use the stable descending key `(published_at, id)`.
- Stage scans use the stable ascending key `(staged_at, stage_id)` and may safely
  commit or abort rows while paging.
- A consistency pass checks at most 10,000 objects and retains at most 1,000
  issues by default. A reconciliation pass checks at most 10,000 stages and
  retains at most 1,000 failures. Constructors allow smaller reviewed budgets.
- An incomplete report exposes an exclusive continuation cursor. Callers
  continuing reconciliation reuse the same cutoff.
- The unreleased V1 external schemas include an
  `(al_id, published_at DESC, id DESC)` index for object keyset scans. The
  existing `(staged_at, stage_id)` index serves stage scans.
- `ArtifactStorePlugin` exposes a property schema. Third-party plugins remain
  open by default for compatibility. Built-in MEMORY, FILESYSTEM and DATABASE
  plugins use closed schemas and reject unknown backend properties.
- Provider-level replication, verification and spool properties remain a
  reserved schema. Numbered fallback child properties are validated by the
  selected child plugin.

## Consequences

Maintenance scheduling must inspect `complete()` and continue from
`nextCursor()` when it is non-null. A report can be successful for the inspected
slice without being complete for the cutoff. Failure and issue omission counters
must be monitored because retained diagnostic lists are intentionally bounded.

Custom `OperationChainObjectRepository` implementations must implement the new
keyset lookup. Staging repositories used by the reconciler must implement the
stage keyset lookup. Third-party store plugins should publish a closed schema
once their property names are stable; leaving the default open preserves their
current behavior but cannot catch typos.
