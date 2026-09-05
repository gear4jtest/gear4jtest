# 2026 audit remediation — phase 3 API and maintainability

**Date:** 4 September 2026
**Baseline:** cumulative phase-2 remediation source tree
**Scope:** generic signal tokens, result nullability, dead JDBC code and JDT compatibility

Product naming and project-wide package, module or artifact renaming remain explicitly out of scope.

## Outcome by audit finding

| Finding | Outcome | Evidence |
| --- | --- | --- |
| C-04 — `MapType` exposes a raw `Map` | Closed | obsolete tokens removed; generic no-token fatal-signal overload and consumer migration example |
| C-05 — `ExecutionResult` nullability is implicit | Closed | JSpecify metadata, safe optional accessors, outcome matrix and staged consumer probe |
| C-06 — weaker unused JDBC transaction helper | Closed | unreferenced `JdbcRepositoryTransaction` removed; active `JdbcTransactionOperations` unchanged |
| A-03 — JDT internal API dependency | Accepted and guarded | imports confined to one `@Internal` adapter; executable Java 17 compatibility test and documented upgrade gate |

## Parameterized fatal signals

`Stations.MapType<U, V>` extended `Type<Map>` and therefore exposed a raw type while claiming a parameterized builder
result. Its key/value class fields were not used at runtime. The duplicate `ElementModelBuilders.MapType` only forwarded
the same token.

Both `MapType` classes and their overloads are removed before 1.0. `Stations.fatalSignal()` now provides type inference
without pretending that a reifiable `Class<Map<K, V>>` exists. The class-token overload remains for ordinary payload
types and delegates to the same builder. The compatibility facade exposes the same no-token overload.

## Explicit `ExecutionResult` nullability

`ExecutionResult` is now `@NullMarked`; its nullable output, trace and error positions are marked with JSpecify
`@Nullable`. The JSpecify annotation artifact is exposed as a small API dependency so downstream Java/Kotlin tooling can
read the contract without redeclaring it.

Existing getters remain source-compatible. The additive `resultOptional()`, `executionOptional()` and `errorOptional()`
methods provide null-safe consumption. Documentation now states the matrix for every outcome. In particular, an empty
result does not imply failure because a successful operator may return `null`; a failed result always has an error and no
output, while a cancellation cause remains optional.

The staged consumer compiles a direct JSpecify reference and uses `resultOptional()`. This guards both published
dependency scope and the additive API after staging.

## Dead transaction helper

`JdbcRepositoryTransaction` had no production or test caller. It duplicated the active `JdbcTransactionOperations`
implementation but accepted an already transactional connection and did not include the active implementation's full
rollback/failure safeguards. Keeping it invited accidental reuse of the weaker path, so the file is deleted without a
replacement or schema change.

## JDT compatibility boundary

The current sources already contained the correct architectural isolation: `JDTInMemoryCompiler` is final,
`@Internal`, selected behind `GeneratedSourceCompiler`, and the source boundary test rejects internal JDT imports in any
other external-api class.

This phase adds the missing executable compatibility gate for the pinned JDT version. It compiles Java 17 source using a
record, a sealed interface and pattern matching, verifies class-file major version 61, loads the emitted classes and
executes the generated method. A second case verifies that invalid source produces non-empty file-associated diagnostics.

Moving JDT to a separate optional module is deliberately deferred. Doing so would change default fallback availability,
factory placement and published dependency behavior; it is not required to contain the internal API risk before 1.0 and
would be disproportionate without connected publication/consumer validation.

## Required validation

Run in a connected repository:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*BuilderFacadesTest' \
  --tests '*ExecutionResultTest'
./gradlew :gear4jtest-external-api:test --tests '*JdtCompilerCompatibilityTest'
./gradlew clean check
./gradlew stageMavenCentral consumerSmokeTest
```

The JDT compatibility test is mandatory whenever `eclipse-jdt-core` changes. Dependency locking and verification
metadata remain post-1.0 work.

## Validation in the audit environment

- The changed core production classes compile with Java 17 and `-Xlint:all` against minimal API-compatible JSpecify
  stubs; no raw-type warning remains in the changed slice.
- A standalone core harness passes for the generic fatal-signal builder, all three optional result accessors and runtime
  visibility of the JSpecify nullability metadata.
- All eight changed Java source/test/consumer files parse successfully with the Java 17 compiler frontend.
- The repository's published-API boundary analyzer passes, and a separate source check confirms that only
  `JDTInMemoryCompiler` imports `org.eclipse.jdt.internal.*`.
- The version catalog parses, the removed `MapType` and transaction helper have no remaining source declaration or use,
  and the phase does not modify `gear4jtest-gradle-xml2java`.
- All 158 repository-local Markdown files considered by the documentation-link gate have valid local links; the nine
  Python release-tool tests pass and all shell scripts pass `bash -n`.
- The real JDT compatibility test, JUnit, Spotless, Checkstyle, publication and consumer smoke tasks could not start
  because the wrapper needs `gradle-9.6.1-bin.zip` from an endpoint unavailable in this environment. They remain
  mandatory before merge.
