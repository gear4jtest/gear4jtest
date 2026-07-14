# Phase 1 release hardening

**Date:** 14 July 2026
**Baseline:** [phase 0 remediation baseline](phase-0-remediation-baseline-2026-07-14.md)

## Scope

This phase completes the source-side release contract without changing runtime behavior. It covers legal assets,
JReleaser configuration validation, staged Maven artifact inspection, local documentation-link validation and release
documentation accuracy.

## Implemented controls

- Added the authoritative Apache-2.0 `LICENSE` and Gear4J `NOTICE` at repository root.
- Package both legal files under `META-INF` in every published JAR variant, including source and Javadoc JARs.
- Added `verifyReleaseAssets` for required-file and configuration consistency checks.
- Added `verifyDocumentationLinks` and wired it into `check`; the obsolete phase-7 overlay link was removed.
- Added `releaseMetadataCheck`, combining legal/link checks with the JReleaser `jreleaserConfig` model validation.
- Added `verifyStagedReleaseArtifacts`, which rejects staged JARs without legal entries and POMs without the Maven
  Central name, description, URL, Apache-2.0, developer or SCM metadata.
- Wired staged-artifact validation into `releaseCheck` and retained the autonomous consumer validation.
- Added release-metadata validation to ordinary CI and the release workflow before credentials are used.
- Added full Git history checkout to the publishing job for JReleaser metadata resolution.
- Corrected the audit closure matrix: reverse artifact orphans remain undetectable with the current store SPI.
- Documented that the current Maven-only workflow does not require a committed `CHANGELOG.md`.

## Validation performed in this environment

The following source-level checks passed:

- YAML parsing for `jreleaser.yml`, `ci.yml` and `release.yml`;
- required legal-file content checks;
- all 67 repository Markdown files passed the local-link validation logic;
- the obsolete missing `scripts/cleanup-phase7-overlay.sh` / `docs/migration-phase7-overlay.md` references are gone.

The Gradle tasks could not be executed because the wrapper distribution was not cached and DNS resolution for
`services.gradle.org` failed in the execution container. The delivery host or connected CI must therefore run the
commands below before the release gate is considered dynamically qualified.

## Required connected validation

```bash
./gradlew spotlessApply
./gradlew releaseMetadataCheck
./gradlew clean releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

Inspect `build/reports/release/staged-artifacts.txt` and the JReleaser logs before enabling a non-dry deployment.

## Exit status

The phase-1 implementation is complete in source. Dynamic Gradle/JReleaser qualification remains pending on a host
with Gradle distribution and dependency access.
