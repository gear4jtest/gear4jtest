# Remediation F-08 - Documentation synchronization and governance, phase 11

## Scope

This phase closes F-08 from the 9 August 2026 audit. It synchronizes the roadmap with the delivered GEL and Micrometer
surfaces, corrects the root README wording, and makes ownership and review dates explicit for living documents.

## Corrected documentation contract

- `docs/roadmap/future-work.md` now distinguishes `DELIVERED`, `REVIEW`, `BACKLOG` and `DEFERRED` entries. Every item
  carries a target version, an ADR or reference when available, and a last-verification date.
- Restricted GEL and trusted inline Java are recorded as delivered 1.0 boundaries. Only post-MVP syntax and function
  extensions remain in the backlog.
- The architecture and module Micrometer documentation record run/station outcomes, durations, cancellations and event
  reaction counters as delivered. Persistence-flush duration, timeout categorization and parallel-branch signals remain
  explicit post-1.0 backlog items.
- Every roadmap note plus the living Micrometer documents declares a status, an owner and an ISO review date.
- The duplicated wording in the root README is removed.

## Regression guard

`verifyLivingDocumentationMetadata` validates all roadmap notes and both Micrometer living documents. It rejects a
missing status, owner or review date, and rejects review dates that are not valid ISO `YYYY-MM-DD` values. The task is
part of both `check` and `releaseMetadataCheck`.

## Explicit non-goals

- No runtime, API, metric or publication behavior changes.
- No commitment to an unscheduled backlog item.
- No dependency locking, `verification-metadata.xml` or other advanced supply-chain enforcement before 1.0.

## Qualification

```bash
./gradlew verifyLivingDocumentationMetadata verifyDocumentationLinks
./gradlew buildLogicCheck
./gradlew check
```
