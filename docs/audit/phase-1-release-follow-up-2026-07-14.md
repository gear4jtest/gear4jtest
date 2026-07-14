# Phase 1 release follow-up — isolated JReleaser and consumer repositories

## Trigger

The first connected execution exposed two independent release-path failures:

1. the autonomous consumer configured only the staged Gear4J Maven repository for plugin resolution, so public
   transitive dependencies such as SLF4J and Eclipse JDT could not be resolved;
2. Spotless 8 and JReleaser 1.x shared the root plugin classpath with incompatible JGit major versions, causing
   `NoClassDefFoundError: org/eclipse/jgit/lib/GpgObjectSigner`.

## Changes

- The consumer now resolves Gear4J groups and both plugin-marker groups only from `build/staging-deploy`.
- Maven Central is available only for non-Gear4J third-party dependencies.
- The smoke invocation uses `--refresh-dependencies` to avoid validating stale artifacts after restaging the same
  prerelease version.
- JReleaser was removed from the root plugin classpath and moved to the isolated `release-tools` included build.
- Root aliases preserve the commands `jreleaserConfig` and `jreleaserDeploy`.
- The isolated build uses JReleaser 1.25.0, reads `PROJECT_VERSION` explicitly, uses the repository root as JReleaser
  basedir, and writes logs under `build/release-tools/jreleaser`.
- `jreleaserDeploy` fails early when no explicit release version reaches the isolated build, avoiding a silent snapshot
  no-op.

## Commands to rerun

```bash
./gradlew releaseMetadataCheck
./gradlew clean releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

The second and third commands must remain separate: `releaseCheck` stages and validates the Maven repository before
JReleaser consumes it.

## Validation status

Static validation passed for release assets, repository routing, YAML syntax and all local Markdown links. The Gradle
wrapper could not be executed in the implementation environment because the Gradle distribution was not available
in cache and external DNS resolution was unavailable. The commands above therefore remain the connected validation.
