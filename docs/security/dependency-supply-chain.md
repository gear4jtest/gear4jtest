# Dependency security baseline for 1.0

Gear4J deliberately keeps the dependency supply-chain policy minimal for the 1.0 release line.

## Enforced for 1.0

- the Gradle wrapper distribution is checksummed;
- GitHub Actions are pinned by commit SHA;
- repositories are limited to the expected Maven Central and staging locations;
- OWASP Dependency-Check runs with automatic updates, fail-on-error behavior and a CVSS 7.0 failure threshold against
  published/runtime dependency configurations;
- the Gradle `checkstyle` tool configuration is excluded because its Doxia/Saxon dependencies are build-tool implementation details and are not shipped in Gear4J artifacts;
- the Sonatype Guide OSS Index analyzer is explicitly disabled for the 1.0 gate because authenticated, credit-metered access is now mandatory; NVD remains the vulnerability source of record for this gate;
- `releaseCheck` requires either an authenticated public NVD API (`NVD_API_KEY`) or an explicit Dependency-Check NVD
  datafeed mirror (`NVD_DATAFEED_URL`), while anonymous access remains available only to ad-hoc scans;
- vulnerability suppressions must be narrowly scoped, documented and time-bounded;
- known Dependency-Check CPE/product-name false positives for Eclipse Platform runtime bundles are suppressed by PURL only and expire for mandatory re-review;
- `gear4jtest-spring-boot-starter` pins the Log4j API/SLF4J bridge line to 2.25.5 until the Spring Boot 3.5 dependency management line incorporates the same or a newer patched version;
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
NVD_API_KEY='<redacted>' ./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```

For an internal mirror, replace the API key with an NVD datafeed template:

```bash
NVD_DATAFEED_URL='https://nvd.example.test/nvdcve-{0}.json.gz' \
  ./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
```

Mirror authentication is optional at the build level because a mirror may be network-restricted. When needed, configure
either `NVD_DATAFEED_USER` plus `NVD_DATAFEED_PASSWORD`, or `NVD_DATAFEED_BEARER_TOKEN`; the release preflight rejects
partial or conflicting credentials and never writes their values to its evidence report.
