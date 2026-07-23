# Phase 12 - Final build, reproducibility and quality hardening

**Date:** 23 July 2026

## Scope retained for 1.0

Phase 12 keeps the parts that improve the release without imposing the advanced Gradle trust workflow:

- Java 17 toolchains and `--release 17` for Java compilation;
- JDBC and Testcontainers dependencies limited to the JDBC modules;
- reproducible archive configuration;
- two-build comparison of staged JAR, POM and Gradle module metadata;
- OWASP Dependency-Check with a CVSS 7.0 threshold;
- reviewed, expiring vulnerability suppressions;
- expanded class and module coverage ratchets.

## Supply-chain controls deliberately deferred

The following items are excluded from the 1.0 gate by project decision:

- dependency lockfiles;
- Gradle verification metadata;
- strict dependency-verification flags;
- CI/release enforcement based on those files;
- the supply-chain bootstrap script.

The existing baseline remains: checksummed Gradle wrapper, SHA-pinned GitHub Actions, controlled repositories,
vulnerability scanning and reproducible staged artifacts. Advanced dependency pinning will be handled in a separate
post-1.0 phase.

## Commands

```bash
./gradlew spotlessApply
./gradlew check
scripts/verify-reproducible-staging.sh 1.0.0-rc1
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```

## Validation note

The implementation does not change Java production or test sources. It changes Gradle configuration, workflows,
scripts, thresholds and documentation only.
