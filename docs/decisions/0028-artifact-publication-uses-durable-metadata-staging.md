# ADR 0028: Artifact publication uses durable metadata staging

## Status

Accepted — 16 July 2026.

## Context

External publication previously wrote an artifact before translating and compiling it and before publishing object
metadata. A validation or metadata failure could therefore leave content in a backend without any durable record from
which Gear4J could discover, reconcile or explain that write. The generic `ArtifactStore` contract intentionally supports
multiple providers and does not expose global enumeration or deletion, so a safe cross-provider garbage collector cannot
be implemented by assuming database semantics.

Deleting a content-addressed object as compensation is also unsafe: the same hash may already be referenced by another
assembly line or publication, and an exception from `put` does not prove that the provider failed before making the bytes
durable.

## Decision

`AssemblyLineManager` validates identifiers, tags, size, media type, translation and generated-source compilation before
writing an artifact. It then resolves the exact artifact-store configuration, computes a stable fingerprint for that
configuration and publishes through a durable staged lifecycle:

1. `OperationChainPublicationRepository.stage(...)` stores invisible object metadata, tags and the store fingerprint;
2. the configured `ArtifactStore` receives the bytes;
3. `commit(stageId)` atomically exposes the object and tags and removes the stage.

A store or metadata-commit failure leaves the durable stage in place because the outcome of an external write may be
ambiguous. A store-resolution failure occurs before stage creation and before any artifact operation, so no recovery
record is needed.

An idempotent retry of the same publication renews the stage timestamp and increments a revision. The reconciler only
aborts a missing-artifact stage when that revision is unchanged. This prevents a stale reconciliation snapshot from
deleting metadata while a renewed upload is still active.

`ArtifactPublicationReconciler` processes stages older than a caller-selected grace period:

- if the current store configuration fingerprint differs from the staged fingerprint, it retains the stage and reports
  the mismatch without probing a potentially unrelated backend;
- if the expected content hash exists, it commits the stage;
- if the hash is missing and the stage revision is unchanged, it aborts the stage;
- if a concurrent retry renewed the stage, it retains it for a later pass;
- if the store or metadata repository cannot be checked, it records the failure and leaves the stage untouched.

The JDBC repository persists stages and stage tags transactionally. The in-memory implementation provides the same
behavior within one JVM, without durability.

## Consequences

- Invalid source is rejected before artifact storage.
- Every artifact write initiated by `AssemblyLineManager` has a durable metadata stage first.
- A crash between artifact storage and metadata visibility is recoverable without deleting shared content.
- Applications must schedule reconciliation and choose a grace period longer than the maximum legitimate upload time.
- Applications should avoid changing a store configuration while old stages exist. A mismatch is retained for explicit
  operational resolution rather than guessed against the new backend.
- The generic store SPI still cannot enumerate artifacts created by older versions or written outside the manager; those
  legacy reverse orphans remain outside this guarantee.
- Custom publication repositories used by `AssemblyLineManager` must implement stage renewal, commit, conditional abort
  and staged lookup.
- The JDBC V1 schema changes directly because Gear4J is still pre-production; existing local schemas must be recreated or
  migrated manually.
