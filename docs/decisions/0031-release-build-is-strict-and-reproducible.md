# ADR 0031: The 1.0 release build is reproducible while advanced dependency verification is deferred

## Status

Accepted for the 1.0 release line.

## Context

Gear4J needs a dependable Java 17 and Maven Central build. Previous attempts to make Gradle dependency locking and
verification metadata mandatory created disproportionate maintenance and bootstrap complexity before 1.0.

## Decision

For 1.0, Gear4J enforces:

- Java 17 toolchains and `--release 17`;
- narrowly scoped database test dependencies;
- reproducible archive ordering and timestamps;
- two-build SHA-256 comparison of staged component artifacts;
- OWASP Dependency-Check with reviewed, expiring suppressions;
- checksummed Gradle wrapper and SHA-pinned GitHub Actions.

Gear4J does not require dependency lockfiles, Gradle verification metadata or strict dependency-verification flags for
1.0. These controls are deferred to a separate post-1.0 phase and must not be introduced implicitly through CI or
`releaseCheck`.

## Consequences

The 1.0 build remains practical to maintain while preserving concrete reproducibility and vulnerability gates. The
project accepts that transitive dependency resolution is not cryptographically pinned yet. This residual risk is
recorded rather than hidden and will be revisited after the release workflow is stable.
