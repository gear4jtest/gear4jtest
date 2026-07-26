# Remediation R3 — Spring API and published boundaries

**Date:** 26 July 2026
**Findings:** F-03 and F-15

## Scope

R3 removes the unused Spring engine-builder hook left behind by the executor-facade migration and replaces the
core-only textual boundary check with a signature-aware check for every published library module.

## Implemented changes

- Removed the obsolete Spring engine-builder customization interface. The supported hook remains
  `Gear4jAssemblyLineExecutorCustomizer`.
- Added the stable `EventPublisher` capability to `ExecutionServices`; the concrete event manager and run-local
  Micrometer binding remain explicitly internal.
- Promoted `PersistenceExtension` and `JdbcStatementOptions`, which were already returned or accepted by documented
  public builders, instead of pretending those signatures were internal.
- Made the translator constructor that accepts internal XML parser/model/generator types private; supported callers use
  the default, `gelOnly(...)` or `trusted()` factories.
- Generalized the architecture rule to all modules carrying package stability markers.
- Built the internal-type catalog from `@Internal` package markers and individual type markers instead of maintaining
  three hard-coded core package prefixes.
- Parsed Java 17 syntax trees and inspected only exported type, method, constructor, field, record-component and
  generic signatures.
- Honored `@Internal` on types and exported members, which keeps intentional implementation hooks outside the stable
  compatibility surface.
- Added focused regression tests proving that a private implementation import is accepted while the same type in a
  public signature is rejected.

## Resulting contract

Published packages marked `@PublicApi` or `@Spi` cannot expose a type from a package marked `@Internal`, or a type
individually marked `@Internal`. The rule covers core, experimental cache, external API and JDBC adapters, Jackson,
JDBC, Micrometer, Spring, the Spring Boot starter and XML.

The source parser runs with the Java 17 language level and is part of `gear4jtest-core:test`, therefore the rule is
also exercised by the root `check` lifecycle.

## Validation

Run on a connected JDK 17 development host:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test :gear4jtest-spring:test --warning-mode=all
./gradlew check --warning-mode=all
```

The constrained audit environment does not contain `javac` or a cached Gradle 9.6.1 distribution. Dynamic Gradle
qualification therefore remains a local/CI gate.
