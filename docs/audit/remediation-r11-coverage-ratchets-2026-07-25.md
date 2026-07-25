# Remediation R11 — Complete and evidence-based coverage ratchets

**Date:** 25 July 2026

## Scope

R11 addresses audit finding F-10 without inventing coverage ratios that have never been measured on a connected build.
The change covers every published module, makes the next green JaCoCo run produce reusable calibration evidence and
prevents pull requests from weakening an existing threshold.

## Guarantees

- Every project published by the main build has exactly one module line-coverage threshold.
- Module ratios cannot be lower than 30%; critical-class branch ratios cannot be lower than 50%.
- Duplicate, stale or missing module/class entries fail `verifyCoveragePolicy`.
- Unit and integration execution data feed both verification and calibration.
- `coverageReport` emits aggregate, per-module and machine-readable calibration reports.
- Suggested thresholds retain two percentage points of margin and never lower the current ratchet.
- Pull requests compare policy files with their target branch and reject removed or reduced thresholds.

## Deliberate limitation

The July audit records the effective coverage as unknown because Gradle could not start in the audit environment. R11
therefore does not pretend that 30% is a final target and does not raise critical classes blindly. The first connected
green Java 17 report must be retained, then thresholds should be raised in small increments. P1 classes should move
toward 70–80% branch coverage; 100% is not a goal by itself.

## Qualification

```bash
./gradlew spotlessApply
./gradlew clean coverageVerification coverageReport \
  -Pgear4jDatabaseDialect=postgresql
cat build/reports/jacoco/coverage-calibration.json
```

After reviewing that report, update only thresholds supported by the observed ratios and rerun `coverageVerification`.
The complete four-dialect JDBC matrix remains a separate mandatory release qualification.
