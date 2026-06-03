# Dependency supply-chain controls

Gear4J uses three complementary Gradle/CI controls:

1. dependency locking, to make resolved versions intentional;
2. dependency verification, to verify checksums/signatures of resolved artifacts;
3. SCA, to detect known vulnerable components.

These controls solve different problems and should remain enabled together.

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

This is a lightweight smoke test. The `help` task configures the build and
resolves enough artifacts to validate that dependency verification is active, but
it is not a replacement for the full build. The full build should still run:

```bash
./gradlew --no-daemon check verifySupplyChainConfiguration
```

`verifySupplyChainConfiguration` verifies that the expected supply-chain files
are present or clearly reported. It is intentionally separated from `check` so CI
can make this policy visible.

## SCA

CI also runs OWASP Dependency-Check:

```bash
./gradlew --no-daemon dependencyCheckAggregate
```

SCA identifies dependencies with known vulnerabilities. Dependency verification
does not do this; it only proves that the artifacts resolved are the artifacts
that were trusted when metadata was generated.

## Recommended CI behavior

For pull requests:

```bash
./gradlew --no-daemon --dependency-verification strict help
./gradlew --no-daemon check verifySupplyChainConfiguration
```

For scheduled security scans:

```bash
./gradlew --no-daemon dependencyCheckAggregate
```

If `verification-metadata.xml` has not been committed yet, the strict verification
step should be treated as a bootstrap TODO. Once the file is committed, missing or
changed metadata should fail CI until reviewed intentionally.
