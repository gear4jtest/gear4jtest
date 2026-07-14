# Phase 1 follow-up — required JReleaser provider

**Date:** 14 July 2026

## Symptom

After disabling the GitHub releaser, `jreleaserConfig` failed with:

```text
No release provider has been configured
```

## Root cause

JReleaser 1.25 validates a release provider even when the selected operation is only Maven deployment. A disabled
GitHub provider is treated as no provider at all. The JReleaser maintainer documents that an enabled provider and a
non-empty token are currently mandatory, although the token does not need to be valid when no provider operation is
performed.

## Final configuration

- The GitHub provider stays enabled only as model metadata.
- `skipTag: true` prevents tag creation.
- `skipRelease: true` prevents GitHub Release creation.
- `uploadAssets: NEVER` prevents asset upload.
- Changelog generation is disabled.
- `release-tools/gradle.properties` contains `jreleaser.github.token=not-used-maven-central-only`.

The value is intentionally not a secret or credential. It is scoped to the isolated release build and exists only to
satisfy JReleaser's non-blank validation. Maven Central and GPG credentials remain supplied exclusively by the release
environment.

`verifyReleaseAssets` checks the complete inert-provider sequence and requires exactly the scoped placeholder property,
so a real GitHub token cannot be committed accidentally as part of this mechanism.

## Static validation completed

- JReleaser and GitHub Actions YAML parsed successfully.
- The inert provider and scoped property invariants were checked.
- 72 Markdown files were checked with no broken repository-local link.

The Gradle/JReleaser execution must still be confirmed on a machine that can resolve the Gradle wrapper distribution.

## Validation command

```bash
./gradlew releaseMetadataCheck
```

A snapshot validation may still report that the Maven Central deployer is inactive because it is configured with
`active: RELEASE`; that warning is expected.
