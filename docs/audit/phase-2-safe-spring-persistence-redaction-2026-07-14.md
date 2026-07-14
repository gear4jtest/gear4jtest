# Phase 2 — Safe Spring Boot persistence redaction

**Date:** 14 July 2026
**Scope:** `gear4jtest-spring-boot-starter` redaction defaults and documentation

## Objective

Prevent JDBC persistence enabled through Spring Boot from storing raw inputs,
contexts, results and error messages when the application has not made an
explicit confidentiality decision.

## Changes

- Added `RedactionMode.DISCARD` and made it the default.
- Missing redactors now resolve to
  `SensitiveDataRedactor.discardSensitiveValues()` in the default mode.
- Preserved `REQUIRE` fail-fast behavior.
- Preserved `DISABLED` as an explicit raw-capture opt-in.
- Retained `WARN` as a deprecated pre-1.0 compatibility mode; it remains an
  explicit raw-capture choice and triggers the existing persistence warning.
- Explicit application-provided redactor beans continue to take precedence.
- Added auto-configuration tests that inspect the effective redactor installed
  in `DatabaseExecutionManager` and assert that default inputs, results, error
  messages and contexts cannot survive redaction.
- Added tests for explicit `DISABLED`, custom redactors and deprecated `WARN`.
- Updated starter, architecture, runtime, production-readiness and root
  documentation with the migration path.

## Compatibility

This is an intentional pre-1.0 security-default change. Applications that
previously relied on the starter's implicit raw capture must now either provide a
`SensitiveDataRedactor` bean or set
`gear4j.persistence.redaction-mode=DISABLED` explicitly.

No database schema or migration change is required.

## Validation

Recommended commands:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-spring-boot-starter:test
./gradlew check
```

The Gradle distribution was unavailable in the implementation environment, so
these commands require execution in a network-enabled or already-cached local
workspace.
