# ADR 0027: External publication requires an atomic capability

## Status

Accepted — 16 July 2026.

## Context

External assembly-line publication persists an immutable object record and zero or more searchable tags. The previous
manager accepted independent object and tag repositories. When no `OperationChainPublicationRepository` was available,
it inserted the object first and then added tags one by one.

A failure during tag insertion left a visible object with incomplete tags. Retrying could then conflict with the existing
object and could not reliably reconstruct the intended publication. The guarantee also depended implicitly on the concrete
repository type detected at runtime.

## Decision

`AssemblyLineManager` requires `OperationChainPublicationRepository` when it is built.

The capability may be provided in either of two ways:

1. explicitly through `Builder.publicationRepository(...)`; or
2. by an `OperationChainObjectRepository` that also implements `OperationChainPublicationRepository`.

The manager rejects configurations that provide only independent object and tag repositories. The publication service no
longer contains a sequential fallback.

The SPI contract requires:

- all-or-nothing visibility of the object and requested tags;
- idempotency for the natural key `(assemblyLineId, version, mode)` when content and metadata match;
- rejection of conflicting content or metadata without changing existing state.

`OperationChainObjectRepositoryJdbc` implements the capability with one database transaction. The provider-neutral module
also supplies `InMemoryOperationChainRepository` for tests and small single-process deployments; its publication and reads
are synchronized on one repository instance.

## Consequences

- A manager cannot start with a repository combination that may leave partial metadata.
- Existing custom repositories must implement the publication SPI or provide a dedicated atomic adapter.
- The object and tag repositories remain separate read/administration contracts.
- The in-memory implementation is atomic only inside one JVM and is not durable.
- This intentional pre-1.0 fail-fast change may break configurations that relied on the unsafe fallback.
