# Phase 8 — Mandatory atomic external publication

## Scope

This phase closes audit finding A03/F27. It changes only external metadata publication and its tests/documentation; artifact
staging and orphan cleanup remain phase 9 work.

## Implemented changes

- `AssemblyLineManager` now requires `OperationChainPublicationRepository` at build time.
- An object repository implementing the publication SPI is still auto-detected.
- The sequential `objectRepository.insert(...)` followed by `tagRepository.addTag(...)` fallback has been removed.
- `AssemblyLinePublicationService` always performs one publication call.
- The SPI Javadoc now explicitly requires all-or-nothing visibility, idempotency and conflict isolation.
- `InMemoryOperationChainRepository` provides thread-safe object, tag and atomic publication contracts for tests and
  small single-process deployments.
- Manager tests cover explicit capability, auto-detection and fail-fast rejection.
- In-memory tests cover idempotent publication, conflict rollback and latest-RUN ordering.

## Compatibility

No public method was removed. Building `AssemblyLineManager` with only independent object and tag repositories now fails
with an `IllegalStateException`. This is an intentional pre-1.0 contract hardening.

## Remaining work

Artifact storage still occurs before compilation and metadata publication. Staging, cleanup and reverse-orphan detection
remain assigned to phase 9.

## Follow-up

The JaCoCo branch-ratchet follow-up is documented in
`docs/audit/phase-8-publication-service-coverage-hotfix-2026-07-16.md`.
