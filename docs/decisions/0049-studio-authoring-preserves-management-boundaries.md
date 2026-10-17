# 0049 — Studio authoring preserves management boundaries

Status: accepted for the experimental Studio modules.

## Context

P2 builds authoring on P1. A runtime getter bypassed resource use cases, access
management permitted arbitrary delegation, and chain definitions and storage
were not bound to stable executable/context identities. Public UI lived in a demo.

## Decision

Encapsulate StudioRuntime behind authenticated metadata and resource-scoped
management use cases. Restrict MANAGE_ACCESS delegation; retain full resource
and domain ownership and host administration. Protect inheritance and rights
that create ownership or permit onward delegation.

Bind the management store atomically to RuntimeContext.id and each chain to an
immutable runtimeAssemblyLineId. Keep build and catalog identities independent.
Consolidate unreleased V1 without silently converting existing histories.

Infer Operator contracts through inherited generic signatures, preserve evidence
and uncertainty, and use explicit structural metadata when Java is insufficient.
Analyze without compilation while editing; compile on explicit validation.

Use stable node identity for semantic comparison and a declared positional
fallback for duplicate IDs. XML preview is non-persistent and normalization is
semantic. Unsupported grammar fails closed. Extract public ESM/Web Components
into a package used by the demos. Bound ACL scans with scoped opaque cursors.

## Consequences

Experimental P0/P1 APIs and V1 checksums change. Existing data needs an explicit
identity-aware migration. Deployment control-plane, durable commands/streams,
additional grammar and generic test payload codecs are not silently introduced.
Real-world contracts, portal integration and ACL volume require further prototypes.
