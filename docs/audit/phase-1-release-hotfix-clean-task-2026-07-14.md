# Phase 1 follow-up — release-tools clean task

**Date:** 14 July 2026
**Scope:** Gradle composite build wiring only

## Symptom

Running an ordinary root task such as `./gradlew spotlessApply` failed while configuring the included
`release-tools` build:

```text
A problem occurred configuring project ':release-tools'.
> Task with name 'clean' not found in project ':release-tools'.
```

## Root cause

The isolated build applied the JReleaser plugin without applying Gradle's `base` plugin. The JReleaser Gradle plugin
expects the conventional lifecycle task `clean` to exist while it configures its tasks. Since included builds are
configured as part of the composite, the missing task also broke unrelated root tasks.

## Correction

`release-tools/build.gradle` now applies the built-in `base` plugin before JReleaser:

```groovy
plugins {
    id 'base'
    id 'org.jreleaser' version '1.25.0'
}
```

The `base` plugin creates the standard `clean`, `assemble`, `check` and `build` lifecycle tasks without adding Java or
publishing dependencies. JReleaser remains isolated from the root Spotless/JGit classpath.

## Expected validation

```bash
./gradlew spotlessApply
./gradlew releaseMetadataCheck
./gradlew clean releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

No runtime or production source code was changed.
