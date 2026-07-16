# Phase 8 follow-up — AssemblyLinePublicationService branch coverage

**Date:** 2026-07-16
**Scope:** `gear4jtest-external-api` tests only

## Trigger

`jacocoCriticalCoverageVerification` reported a branch ratio of 0.50 for
`io.github.gear4jtest.external.api.AssemblyLinePublicationService`, below the required 0.60 ratchet.

The phase 8 atomic-publication refactoring preserved the existing threshold but did not exercise all branches of the
publication service through `AssemblyLineManagerTest`.

## Added scenarios

The test suite now covers the previously missing outcomes:

- `version == null`;
- non-null but blank version;
- direct `RUN` publication explicitly allowed by configuration;
- latest-RUN alias invalidation after direct `RUN` publication;
- `null` tags normalized to an empty list;
- promotion when an identical `RUN` object already exists;
- promotion rejection when an existing `RUN` has a different content hash.

Together with the existing tests, every explicit conditional outcome in `AssemblyLinePublicationService` is now
exercised:

| Conditional | Covered outcomes |
|---|---|
| version null check | null / non-null |
| version blank check | blank / non-blank |
| direct RUN policy block | TEST / RUN |
| allow direct RUN | allowed / rejected |
| post-publication alias invalidation | RUN / non-RUN |
| existing RUN during promotion | present / absent |
| existing RUN content hash | equal / different |
| publication tags normalization | null / non-null |

## Production impact

None. No production source or coverage threshold was changed.

## Recommended validation

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test --tests '*AssemblyLineManagerTest'
./gradlew jacocoCriticalCoverageVerification
./gradlew check
```
