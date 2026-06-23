# Dependency supply-chain controls

Gear4J keeps the following controls as release hardening options. They are not required for the MVP development loop and should not block ordinary feature work:

1. dependency locking, to make resolved versions intentional;
2. dependency verification, to verify checksums/signatures of resolved artifacts;
3. SCA, to detect known vulnerable components.

These controls solve different problems. They should be considered together for a future strict Maven Central release gate, but strict enforcement remains an explicit release decision.

## Dependency locking

Dependency locking records resolved module versions. Regenerate locks only when a
dependency update is intentional:

```bash
./gradlew dependencies --write-locks
```

The generated lock files must be reviewed and committed.

## Dependency verification

Gradle dependency verification checks that resolved artifacts match trusted
checksums/signatures. Bootstrap metadata from a trusted developer machine:

```bash
./gradlew --write-verification-metadata sha256 help
```

The generated `gradle/verification-metadata.xml` must be committed and reviewed.
After it exists, CI can run:

```bash
./gradlew --no-daemon --dependency-verification strict help
```

This is a lightweight smoke test for a future hardened release lane. The `help` task configures the build and
resolves enough artifacts to validate that dependency verification is active, but
it is not part of the MVP development loop.

`verifySupplyChainConfiguration` remains available for a future hardened release lane. During the MVP it only reports missing supply-chain files by default and must not block ordinary checks or releases. To resume strict enforcement later without rewriting the task, run it with:

```bash
./gradlew verifySupplyChainConfiguration -Pgear4j.enforceSupplyChain=true
```

Only with this property enabled does a missing `gradle.lockfile` or `gradle/verification-metadata.xml` fail the build. If these files are present, they can be used and reviewed; if they are absent during the MVP, the build should continue. This subject is intentionally not a priority until the runtime/API/release process is stable.

## SCA

CI also runs OWASP Dependency-Check:

```bash
./gradlew --no-daemon dependencyCheckAggregate
```

SCA identifies dependencies with known vulnerabilities. Dependency verification
does not do this; it only proves that the artifacts resolved are the artifacts
that were trusted when metadata was generated.

## Recommended CI behavior

For pull requests during the MVP phase:

```bash
./gradlew --no-daemon check
```

For release candidates during the MVP, keep supply-chain checks opportunistic rather than mandatory:

```bash
./gradlew --no-daemon releaseCheck
```

If `gradle/verification-metadata.xml` already exists and has been reviewed, an additional strict verification smoke test can be run manually:

```bash
./gradlew --no-daemon --dependency-verification strict help
```

For scheduled security scans:

```bash
./gradlew --no-daemon dependencyCheckAggregate
```

If `verification-metadata.xml` has not been committed yet, the strict verification
step should simply be skipped during the MVP. Once the runtime and release process
are stable, the team can deliberately decide whether to make missing or changed
metadata fail CI.


## Dependency surface hygiene

Production dependencies should be declared by the module that actually uses them
instead of being injected into every subproject from the root build. This keeps
optional modules lighter and reduces the transitive surface exposed to consumers.

Current policy:

- `gear4jtest-core` owns SLF4J only; `gear4jtest-jdbc` owns the Jackson API used by JDBC persistence;
- `gear4jtest-external-api` owns SLF4J, Jackson for external repository JSON and
  JDT for the default compiler implementation;
- `gear4jtest-xml` owns SLF4J and Eclipse formatter dependencies;
- optional modules declare their own integration dependencies.

Guava is not part of the current production dependency surface.
