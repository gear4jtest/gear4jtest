# 0048 — Studio P1 management is transactional and scoped

Status: accepted for the experimental P1 implementation, 2026-09-28.

## Context

P0 proved catalogue → declarative definition → controlled Gear4J execution in Java and Spring. Its process-local drafts and creator-only access cannot support a shared team workspace. P1 needs durable authoring state without introducing distributed deployment orchestration.

## Decision

- Add framework-neutral Domain, Chain, Draft, ACL, identity and audit contracts plus management use cases. Keep DraftRevision immutable; resource concurrency versions are separate from revision numbers.
- Require identity from an application-owned CurrentActorProvider on every request. Never trust actor/groups in a request body. Permissions are positive grants with explicit inheritance; READ implies DISCOVER. New drafts are private within the documented domain-owner/host-administrator boundary.
- Persist the declarative model through a separate JSON adapter with a closed node grammar. XML remains a runtime adapter, not the storage model. Saving invalid but storage-safe drafts is supported.
- Add a trusted transactional StudioStore SPI and one JDBC adapter, reusing the existing migration infrastructure. Provide V1 resources for H2 and PostgreSQL. Runtime tables and published artifact repositories remain separate.
- Read under a repeatable snapshot. Serialize mutations per domain, re-read authorization after locking, compare the resource version and append audit in the same transaction. Never hold a management transaction during compilation/execution.
- Use the public service and web components in both Java and Spring examples. Ship a workspace around the existing editor, not a second authoring implementation. The demo HTTP contract moves to /api/v1 and removes the P0 bypass routes.
- Keep short operations synchronous. Audit test admission durably, while process-local test receipts retain P0 semantics. Neither admission audit nor best-effort runtime events constitute a durable run repository.
- Archive/abandon rather than physically deleting authored history. Do not implement ownership transfer or resource moves in this increment.

## Consequences

Domain-level serialization trades write throughput for simple, testable authorization and audit consistency. READ_AUDIT reveals historical access policies, so it is a distinct permission. Owners and application administrators retain access to private drafts; this must be visible in the product and documented.

The migration connection is closed before ordinary use. H2 enum checks use CASE expressions to avoid a closed-session constant-set comparison observed with IN checks on the pinned H2 version; constraints remain enforced. Tests exercise database reopen, atomic rollback and concurrent updates.

Database dependency scope checks explicitly include the new JDBC adapter, H2-only acceptance tests in the neutral management/Spring demo modules, and the non-published local demo DataSource. Production management contracts still have no database driver dependency.

The host owns authentication, group mapping, the DataSource and external security infrastructure. Spring can supply the same provider as a bean without forcing Spring Security on other users. Direct storage access is privileged code, not a secured public API.

Durable definitions do not make tests isolated or runs durable. P1 does not implement publication, promotion, deployments, application inventories, remote targets, durable SSE, simulation or a command broker. These remain separately scoped phases. The adapter contracts leave room for those additions without creating placeholder persistent entities now.
