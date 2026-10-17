# 0050 — Studio publishes pinned artifacts through Gear4J

Status: accepted for experimental Studio P3.

## Context

Authoring snapshots and the Gear4J TEST/RUN artifact repository have different
identities and transaction boundaries. Invalid XML work must survive editing,
but may not enter compilation or publication implicitly.

## Decision

Represent revisions as an exclusive STRUCTURED definition or an exact SOURCE.
Apply the host's data admission policy to the entire source before storage;
reject unclassifiable fragments instead of stripping or partially importing them.
Analysis and structured recovery are explicit user actions.

Freeze a validated structured revision as an immutable StudioVersion, with its
exact UTF-8 XML artifact, hash, chain/runtime identity, revision and catalog/build
provenance. Keep publication labels separate from DSL definition versions.

Use the existing AssemblyLineManager TEST registration and TEST-to-RUN promotion
through an optional host-owned StudioPublisher. Authorize each action on the
chain, separately from draft access, and opt in RUN capabilities explicitly.
Use the same application manifest for catalog metadata and generation policies.

Keep short management APIs synchronous. Persist publication intent and observation
atomically with their respective audit records, without holding a database
transaction during runtime work. Reconcile interrupted requests by observing
exact runtime identity/content; never infer a failed write from a missing response.
Require explicit retries of immutable idempotent operations, no background workflow.

Tests of a frozen version compile its exact stored artifact under the current
build/policies and report both version identity and actual execution provenance.
Version creation deliberately shares that candidate with chain readers; its
private source draft remains under its own ACL.

## Consequences

No additional activation/deployment controller, target model or durable event
stream is needed. Publication confirms an artifact, not deployment to every
runtime. Existing latest ordering/invalidation is preserved; promotion is not a
rollback guarantee. Test receipts remain transient pending P4 run persistence.
The initial unreleased V1 migrations change; existing development databases need
an explicit migration or a separate new database, never automatic deletion.
