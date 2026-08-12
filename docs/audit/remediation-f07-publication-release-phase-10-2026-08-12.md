# Remediation F-07 - Publication and release conventions, phase 10

## Scope

This is the third and final mechanical extraction from the root `build.gradle`. It moves standard Java-library
publication and the root staging/release orchestration into the included `build-logic` build. The Maven coordinates,
module selection, database-test dependencies, Sonar configuration and OWASP policy remain owned by the main build.

## Convention boundaries

- `gear4j.publishing` applies the Java-library convention, creates source and Javadoc JARs, preserves normalized
  runtime inputs and reproducible manifests, configures the local `mavenCentralStaging` repository, and publishes the
  unchanged Maven Central POM metadata.
- Publication configuration uses explicit publishing, repository, publication and POM receivers. This prevents the
  precompiled Groovy script from resolving an unqualified nested `publications {}` block against the target project
  during early cross-project application.
- `gear4j.root-release` verifies legal and JReleaser assets, delegates to the isolated `release-tools` build, stages and
  validates every publication, runs the autonomous consumer, enforces the complete JDBC release selection and Java 17
  archive contract, performs N-1 Japicmp checks, and assembles the existing `releaseCheck` gate.

The Gradle plugin keeps its specialized publication block because `java-gradle-plugin` owns its implementation and
marker publications. Root staging continues to include the implementation artifact and both published plugin markers.

## Preserved public contract

The following task names and outputs are unchanged:

- `verifyReleaseAssets`, `releaseMetadataCheck`, `jreleaserConfig` and `jreleaserDeploy`;
- `stageMavenCentral` and `build/staging-deploy`;
- `verifyStagedReleaseArtifacts` and `build/reports/release/staged-artifacts.txt`;
- `consumerSmokeTest`, `verifyReleaseDatabaseMatrixSelection` and `verifyJava17AndArchiveConfiguration`;
- every `japicmp*` task, `verifyApiCompatibilityConfiguration` and `apiCompatibilityCheck`;
- `releaseCheck` and all of its former dependencies.

The Maven artifact ids, POM names/descriptions, homepage, Apache-2.0 license, developer, SCM tag policy and manifest
attributes are unchanged. `release-tools` remains isolated so JReleaser 1.25.0 and Spotless do not share incompatible
JGit runtimes.

## Regression guard

`build-logic:test` now adds a third isolated TestKit fixture. It applies both new conventions without a target version
catalog, publishes a real Java fixture into a temporary Maven repository, validates every staged JAR and POM, and checks
the exact license, developer, SCM and release-tag metadata, the public release-task model and `releaseCheck`
dependencies. Root `buildLogicCheck` keeps this fixture in `check`.

The fixture loads both convention plugin ids in its root `plugins` block with `apply false` before applying them
imperatively to the root and sample subproject. This mirrors the main build and makes the plugin-under-test classpath
available to cross-project `apply plugin:` calls.

The former applied script `publishing.gradle` is reduced to a two-line compatibility shim for local branches that may
still apply it directly; publication now has one implementation in `gear4j.publishing`, and the main build no longer
uses the shim.

## Explicit non-goals

- No Maven coordinate, POM, manifest, plugin id or release-workflow change.
- No weakening of coverage, performance, JDBC, SCA, staging, reproducibility or API-compatibility gates.
- No dependency locking, `verification-metadata.xml` or strict dependency verification before 1.0.
- No change to the accepted 1.0 supply-chain residual risk.
