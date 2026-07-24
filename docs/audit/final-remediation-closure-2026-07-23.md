# Audit remediation implementation status

**Date:** 23 July 2026
**Last updated:** 24 July 2026

This document records the implementation state of the original remediation program. It is not, by itself, evidence that
the release candidate is qualified. Qualification remains pending until the commands below pass against the exact commit
and their reports are retained.

An independent follow-up audit found two differences between the documented state and the transferred source archive:

- R0 was a transfer-only omission of `release-tools/`, `LICENSE` and `NOTICE`; the canonical project already contained
  them and no source correction was required.
- R1 found obsolete copies left after public API promotion. They were removed from
  `core.execution` and `core.engine.support`, and the build now rejects their return in the core binary or sources JAR.

## Implemented areas awaiting final qualification

- release assets and Maven Central validation;
- safe Spring persistence defaults;
- core concurrency and UUIDv7 robustness;
- concurrent and bounded JDBC persistence shutdown;
- real multi-dialect JDBC validation;
- mandatory atomic publication and staged artifact recovery;
- public API/module boundaries, including the R1 cleanup of obsolete promoted types;
- generated-source compilation and Gradle TestKit cache behavior;
- Java 17 compatibility, archive reproducibility, dependency-test scoping and coverage ratchets.

## Accepted deferred item

Advanced Gradle dependency locking and checksum verification are intentionally deferred until after 1.0. The project
therefore does not require lockfiles, `verification-metadata.xml` or strict dependency-verification flags for build,
CI or release. This is a conscious residual risk, not an accidental omission.

## Final qualification commands

```bash
./gradlew spotlessApply
./gradlew check
scripts/verify-reproducible-staging.sh 1.0.0-rc1
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy \
  -PprojectVersion=1.0.0-rc1
```
