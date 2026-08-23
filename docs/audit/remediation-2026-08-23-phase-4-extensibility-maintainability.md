# 2026 audit remediation - phase 4 extensibility and maintainability

**Date:** 23 August 2026
**Baseline:** cumulative corrected phase-3 source tree
**Scope:** deterministic SPI selection, open store identifiers, JDBC repository
decomposition and defensive generated-bytecode ownership

## Outcome by audit finding

| Finding | Outcome | Evidence |
| --- | --- | --- |
| F-08 - SPI selection depends on classpath order | Closed | duplicate store types rejected, translator/compiler stable ids, ambiguity rejection, explicit-id overloads and aggregated translator probe failures |
| F-09 - closed `StoreType` contradicts the store SPI | Closed before 1.0 | open validated value object, built-in constants, JDBC round-trip and open V1 `VARCHAR(64)` schemas for every dialect |
| F-12 - JDBC repository combines two protocols and mapping policies | Closed incrementally | unchanged public facade with package-private object, stage, tag and row-mapping collaborators; facade reduced from 700 to 370 lines |
| F-13 - classloader retains mutable caller byte arrays | Closed | complete validation/copy before publication to the class map and mutation-after-registration regression coverage |

## Deterministic extension selection

`ArtifactStoreResolver` canonicalizes plugin types through `StoreType` and
rejects a duplicate at construction. Its exposed type set has stable
lexicographic iteration order. A plugin can no longer replace another plugin
silently because it happened to be enumerated later.

`OperationChainTranslator.id()` and `GeneratedSourceCompiler.id()` default to
the implementation class name and may be overridden with a stable application
identifier. Default discovery accepts zero/one compiler and exactly one
applicable translator. Multiple candidates fail with sorted identifiers.
Explicit overloads select one id when an application intentionally installs
several providers.

A translator exception from `supports(...)` now makes resolution fail and is
retained as a suppressed exception. Continuing with another translator would
be unsafe because the failed provider might also support the requested media
type.

No priority integer or implicit class-name tie-breaker was introduced. An
ambiguity is a configuration defect that must be resolved explicitly.

## Open artifact-store identifiers

`StoreType` keeps its public name and built-in constants but is now a record
whose canonical value matches `[A-Z][A-Z0-9_-]{0,63}`. `of(...)` is the primary
factory; `name()` and `valueOf(...)` remain source conveniences for code written
against the pre-1.0 enum.

All external V1 migrations now use `VARCHAR(64)` or Oracle `VARCHAR2(64)` plus a
dialect-appropriate format check. The MySQL/MariaDB built-in-only enum was
removed. `ExternalJdbcMultiDialectIT` now persists and reloads `CUSTOM-STORE`
through the configuration repository.

This is an intentional pre-1.0 source/binary boundary change. Enum-only
`values()`, exhaustive `switch` and enum reflection must migrate before the
compatibility baseline. Because the schema is unreleased, V1 was corrected in
place and no V2 migration was added.

## Incremental JDBC decomposition

The public `OperationChainObjectRepositoryJdbc` still implements both object
lookup and atomic publication, preserving consumer wiring and the transaction
boundary. It now coordinates four internal collaborators:

- `OperationChainObjectJdbcOperations`: object insert, lookup, existence and
  offset/keyset page SQL;
- `OperationChainPublicationStageJdbcOperations`: stage rows, locking,
  revision/deletion and mutation-safe keyset pages;
- `OperationChainTagJdbcOperations`: batched committed/staged tags and stage-tag
  cleanup;
- `OperationChainObjectRowMapper`: dialect-aware object/stage mapping and hash
  normalization.

Savepoints, idempotency, conflict detection and transaction ownership remain in
the facade where the publication protocol can be reviewed as one unit. The
change is an extraction, not a rewrite, and introduces no new public type.

## Defensive bytecode ownership

`InMemoryClassLoader.addCompiledClasses(...)` validates and clones the complete
input map before publishing any class bytes. A custom compiler can reuse, clear
or mutate its buffers after the method returns without changing the bytes later
passed to `defineClass`. Retained/defined byte accounting continues to charge
the cloned byte length and releases heap-retained bytes after definition.

## Regression coverage added

- duplicate artifact-store types in both discovery orders and stable available
  type ordering;
- compiler ambiguity in both orders, duplicate ids and explicit-id selection;
- translator ambiguity, explicit-id selection and aggregated `supports(...)`
  failure diagnostics;
- third-party `StoreType` canonicalization, validation and JDBC mapping;
- V1 schema contract proving open validated store columns and no built-in-only
  MySQL/MariaDB enum;
- caller byte-array mutation after classloader registration;
- multi-dialect third-party store-type persistence;
- existing object/stage keyset characterization retained through the extracted
  JDBC facade.

## Validation in the audit environment

The changed external API, configuration repository, JDBC facade and four JDBC
collaborators compile with Java 17 `jdk.compiler` and `-Xlint:all`. Standalone
harnesses using the changed production classes reported:

```text
PHASE4_ARTIFACT_SPI_PASS
PHASE4_COMPILER_SPI_PASS
PHASE4_TRANSLATOR_SPI_PASS
PHASE4_LOADER_COPY_PASS
PHASE4_JDBC_FACADE_PASS
PHASE3_HARNESS_PASS
MEMORY_HARNESS_PASS
PROVIDER_HARNESS_PASS
SPOOL_HARNESS_PASS
```

The repository Python release-tool tests pass (9/9), and every shell script
passes `bash -n`. Structural guards find no duplicate ADR number, dependency
lockfile, dependency-locking block, verification metadata or V2 external
migration. The documented pre-1.0 dependency policy remains byte-for-byte
unchanged.

The Gradle wrapper still cannot download Gradle 9.6.1 because
`services.gradle.org` is unreachable from this environment. No JUnit, Spotless,
Checkstyle, Testcontainers, Dependency-Check or complete Gradle build result is
claimed. Run in the connected project repository:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*StoreTypeTest' \
  --tests '*ArtifactStoreResolverTest' \
  --tests '*OperationChainTranslatorResolverTest' \
  --tests '*GeneratedSourceCompilersTest' \
  --tests '*InMemoryClassLoaderTest'
./gradlew :gear4jtest-external-jdbc:test \
  --tests '*OperationChainObjectRepositoryJdbcTest' \
  --tests '*OperationChainObjectRepositoryJdbcBehaviorTest' \
  --tests '*OperationChainConfigRepositoryJdbcTest' \
  --tests '*ExternalRepositorySqlDialectContractTest'
./gradlew :gear4jtest-external-jdbc:integrationTest \
  --tests '*ExternalJdbcMultiDialectIT'
./gradlew check integrationTest dependencyCheckAggregate
```

## Residual risks and next phase

- Applications compiled against an earlier pre-1.0 enum must recompile and
  migrate enum-only source before the first compatibility baseline.
- The complete production-dialect constraint syntax and refactored publication
  protocol still require the connected Testcontainers matrix.
- Third-party plugin property schemas remain open unless their author opts into
  a closed vocabulary, as decided in phase 3.
- Phase 5 owns removal of residual wall-clock sleeps, compiler documentation
  alignment, observability/release qualification and replay of all release
  gates (F-14, F-15 and cross-cutting evidence).
- Dependency locking and Gradle verification metadata remain explicitly
  deferred until after 1.0 by project policy; they are not part of phase 5.
