# 1.0 onboarding and migration guidance - phase 18

**Date:** 13 August 2026
**Scope:** progressive first use and pre-1.0 package/module migration

## Objective

Close the remaining documentation items that become actionable after the public execution facade stabilizes: provide a
progressive first pipeline and an evidence-based migration path without turning internal implementation types into
accidental 1.x contracts.

## Delivered guidance

- `docs/tutorial/getting-started.md` progresses from the core dependency through typed operators, resource ownership,
  assembly-line composition, request execution, terminal outcomes and optional integrations.
- `GettingStartedExample` is compiled from the autonomous consumer project against staged artifacts and is executed by
  `ConsumerSmoke`; it must return `Hello, Ada!` with a successful outcome.
- `docs/migration/to-1.0.md` defines the public executor migration, maps all nine removed promoted types, lists explicit
  published artifacts and records the Java, property, database, XML, metric and redaction review points.
- ADR 0040 makes the staged consumer the behavioral source of truth for first-use documentation.
- Root and documentation indexes link both paths; the roadmap marks the two after-stable-API items delivered.

## Validation commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test
./gradlew stageMavenCentral consumerSmokeTest -PprojectVersion=1.0.0
./gradlew check
```

## Sandbox validation status

The configured Gradle 9.6.1 distribution is not cached in the audit sandbox, so the connected commands above remain the
merge gate. Offline Java 17 validation:

- parses every repository Java source;
- compiles the complete core production tree;
- compiles and runs the staged-consumer tutorial example against those core classes;
- verifies the expected successful outcome and `Hello, Ada!` value; and
- checks documentation links, ADR identifiers, living-document metadata, trailing whitespace and final newlines.

This phase adds no dependency lockfile, Gradle verification metadata, durable event delivery, spool replay, JPMS
descriptor, distributed quota or database migration. Their explicit deferred or conditional status remains unchanged.
