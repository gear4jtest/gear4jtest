# 2026-09-05 audit remediation — phase 4 release qualification

## Scope

This cumulative phase implements the final release-qualification actions from the 29 August 2026 audit. Product naming,
package/artifact renaming, dependency locking, Gradle verification metadata and unrelated XML Gradle plugin changes are
explicitly outside scope.

## B-02 — broader compiler diagnostics

`gear4j.java-base` now applies `-Xlint:all,-try,-serial` to every Java compile task. This replaces the former
`-Xlint:unchecked` configuration and exposes raw types, casts, fallthrough and the remaining relevant Java 17 compiler
diagnostics. The reviewed `try` and `serial` categories remain excluded until their existing baseline is cleaned. This
phase deliberately does not add `-Werror`: the audit explicitly recommends progressive adoption rather than turning all
historical warnings into an immediate build outage.

`verifyJava17AndArchiveConfiguration` checks the compiler argument alongside `--release 17`, so the release gate detects
a module that bypasses the convention.

## B-01 — deterministic vulnerability-feed contract

Ordinary `dependencyCheckAggregate` remains usable with anonymous public NVD access. A release is stricter:

- `verifyReleaseVulnerabilityFeed` requires `NVD_API_KEY` or `NVD_DATAFEED_URL`;
- a mirror may use basic credentials or a bearer token, but not both;
- partial credentials or credentials without a mirror URL are rejected;
- the generated `build/reports/release/vulnerability-feed.txt` contains only source/authentication modes, never a URL,
  username or secret;
- the Dependency-Check configuration explicitly keeps automatic updates and fail-on-error enabled;
- `dependencyCheckAggregate` runs after the feed preflight when both tasks belong to `releaseCheck`.

The release workflow injects the same API-key/mirror settings at the scan step. The existing security workflow continues
to use the authenticated public API.

## Retained release evidence

`releaseCheck` now also runs `coverageReport` and rejects a database qualification that does not produce non-empty SQL
plan reports for PostgreSQL, MySQL, MariaDB and Oracle. This closes the gap where Docker-disabled integration tests could
be skipped without leaving the expected dialect evidence.

The final release manifest requires and hashes:

- staged-artifact inspection;
- the non-secret vulnerability-feed contract;
- both reproducibility inventories;
- the Dependency-Check JSON report;
- aggregate JaCoCo XML;
- JMH results;
- all four SQL-plan reports;
- versioned coverage/performance policies and operational security documentation;
- JUnit XML plus Japicmp reports when an N-1 baseline is configured.

The reproducibility script preserves those reports across its internal clean rebuild. The GitHub release artifact now
includes the raw reproducibility, coverage and SQL-plan paths in addition to the final manifest.

## Validation

The following checks are expected in this audit environment without external dependencies:

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
bash -n scripts/bootstrap-dependency-trust.sh scripts/verify-reproducible-staging.sh
```

The authoritative connected release gate remains:

```bash
NVD_API_KEY='<redacted>' ./gradlew --no-daemon --warning-mode=all \
  clean releaseCheck stageMavenCentral -PprojectVersion=1.0.0-rc1
scripts/verify-reproducible-staging.sh 1.0.0-rc1
```

It must run with Docker and network access, preserve all four database reports, then execute the JReleaser dry run and
generate the final evidence manifest as documented in [Releasing](../releasing.md). No positive Gradle, JUnit,
Testcontainers, SCA or publication result is claimed until that connected gate has executed.
