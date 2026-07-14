# Phase 1 follow-up — JReleaser token source correction

**Date:** 14 July 2026

## Problem

`release-tools/gradle.properties` defined `jreleaser.github.token`, but JReleaser does not consume ordinary Gradle project properties as credential sources. The provider therefore still failed validation with `release.github.token must not be blank`.

## Correction

- Removed the ineffective `release-tools/gradle.properties` file.
- Added the fixed non-secret sentinel directly to the inert GitHub provider in `jreleaser.yml`:

```yaml
release:
  github:
    enabled: true
    token: not-used-maven-central-only
    skipTag: true
    skipRelease: true
    uploadAssets: NEVER
    changelog:
      enabled: false
```

JReleaser documents `release.github.token` as a YAML property. The value is not a GitHub credential and cannot authorize any remote operation. GitHub tag creation, release creation, asset uploads and changelog generation remain disabled.

- Updated `verifyReleaseAssets` to require this exact inert provider block.
- Updated the release guide and marked the previous Gradle-property workaround as superseded.

## Validation command

```bash
./gradlew releaseMetadataCheck
```

The Maven Central deployer warning is expected for the snapshot version used by metadata validation because the deployer is configured with `active: RELEASE`.
