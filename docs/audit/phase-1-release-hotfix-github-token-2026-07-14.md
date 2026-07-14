> **Superseded:** JReleaser 1.25 rejects a disabled provider with `No release provider has been configured`.
> See `phase-1-release-hotfix-required-provider-2026-07-14.md` for the final deploy-only configuration.

# Phase 1 follow-up — JReleaser GitHub token validation

**Date:** 14 July 2026

## Symptom

`releaseMetadataCheck` reached `jreleaserConfig` but failed with:

```text
release.github.token must not be blank
```

## Root cause

JReleaser enables its default GitHub release provider unless it is explicitly disabled. Gear4J uses a GitHub tag only
to trigger GitHub Actions; JReleaser is responsible solely for signing and deploying the staged Maven repository to
Maven Central. The workflow therefore had an unintended GitHub token prerequisite.

## Correction

- Added `release.github.enabled: false` to `jreleaser.yml`.
- Kept Maven Central deployment and PGP signing active for release versions.
- Added a `verifyReleaseAssets` guard that requires the explicit GitHub release opt-out.
- Clarified that the tag-triggered workflow does not create a GitHub Release and does not require
  `JRELEASER_GITHUB_TOKEN`.

No dummy token is injected into local or CI validation. A future decision to publish GitHub Releases must be explicit and
include a dedicated token/permissions design.

## Validation command

```bash
./gradlew releaseMetadataCheck
```

The later Maven Central dry-run still requires the release version and the Maven/GPG credentials appropriate to the
selected JReleaser dry-run semantics.
