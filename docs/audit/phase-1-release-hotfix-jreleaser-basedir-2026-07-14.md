# Phase 1 follow-up — JReleaser base directory wiring

**Date:** 14 July 2026
**Scope:** isolated JReleaser Gradle build only

## Symptom

Configuring `release-tools` failed with:

```text
Could not set unknown property 'basedir' for extension 'jreleaser'
```

## Root cause

JReleaser 1.25.0 does not expose `basedir` on `JReleaserExtension`. The base directory used by a JReleaser workflow is the `projectDirectory` property of each JReleaser task. The previous hotfix configured the wrong object.

## Correction

The unsupported extension assignment was removed:

```groovy
jreleaser {
    configFile = file('../jreleaser.yml')
    dependsOnAssemble = false
}
```

The root repository directory is now assigned to the tasks that are delegated by the main build:

```groovy
def repositoryRoot = layout.projectDirectory.dir('..')

tasks.named('jreleaserConfig') {
    projectDirectory.set(repositoryRoot)
}

tasks.named('jreleaserDeploy') {
    projectDirectory.set(repositoryRoot)
}
```

This keeps these paths root-relative:

- `jreleaser.yml` remains at the repository root;
- `build/staging-deploy` remains the staged Maven repository;
- JReleaser output remains under `build/release-tools/jreleaser`.

`verifyReleaseAssets` now rejects any reintroduction of an extension-level `basedir` assignment and verifies the task-level wiring.

## Expected validation

```bash
./gradlew spotlessApply
./gradlew releaseMetadataCheck
./gradlew clean releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

No production source code was changed.
