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

`verifySupplyChainConfiguration` remains available for a future release lane. It reports missing supply-chain files by default. To resume strict enforcement later without
rewriting the task, run it with:

```bash
./gradlew verifySupplyChainConfiguration -Pgear4j.enforceSupplyChain=true
```

With this property enabled, missing `gradle.lockfile` or `gradle/verification-metadata.xml` fails the build. It remains
outside the MVP `check` workflow so CI/release policy can decide when to make the hardening mandatory.

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

For release candidates, strict supply-chain checks can be re-enabled deliberately:

```bash
./gradlew --no-daemon --dependency-verification strict help
./gradlew --no-daemon verifySupplyChainConfiguration -Pgear4j.enforceSupplyChain=true
```

For scheduled security scans:

```bash
./gradlew --no-daemon dependencyCheckAggregate
```

If `verification-metadata.xml` has not been committed yet, the strict verification
step should be treated as a bootstrap TODO. Once the file is committed, missing or
changed metadata should fail CI until reviewed intentionally.
