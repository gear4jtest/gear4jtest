# Dependency security baseline for 1.0

Gear4J deliberately keeps the dependency supply-chain policy minimal for the 1.0 release line.

## Enforced for 1.0

- the Gradle wrapper distribution is checksummed;
- GitHub Actions are pinned by commit SHA;
- repositories are limited to the expected Maven Central and staging locations;
- OWASP Dependency-Check runs with a CVSS 7.0 failure threshold;
- vulnerability suppressions must be reviewed and time-bounded;
- staged JAR, POM and Gradle module metadata are rebuilt and compared by SHA-256;
- legal and release metadata are verified before publication.

## Explicitly deferred

The following controls are intentionally not required for 1.0:

- Gradle dependency lockfiles;
- `gradle/verification-metadata.xml`;
- strict dependency verification;
- CI or release flags that enforce those files.

They must be introduced only through a later dedicated hardening phase, after validating the maintenance and upgrade
workflow. Their absence must not make `build`, `check` or `releaseCheck` fail in the 1.0 line.

Successful release dry runs and publications record this accepted risk in
`build/reports/release/release-evidence.json` together with the commit identity
and hashes of the enforced 1.0 evidence. This makes the deferral visible without
turning the deferred controls into an implicit gate.

## Current commands

```bash
./gradlew check
./gradlew dependencyCheckAggregate
scripts/verify-reproducible-staging.sh 1.0.0-rc1
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```
