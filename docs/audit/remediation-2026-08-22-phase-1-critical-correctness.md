# 2026 audit remediation — phase 1 critical correctness

**Date:** 22 August 2026
**Scope:** vulnerable integration drivers, concurrent artifact-store identity and
JDBC chained-failure classification

## Remediation roadmap

The 22 August 2026 audit is split into five incremental phases. The phase
boundaries keep runtime invariants reviewable and avoid combining API redesigns
with urgent correctness fixes.

| Phase | Scope | Audit findings |
| --- | --- | --- |
| 1 — critical correctness | Secure JDBC integration coordinates, atomic store resolution and complete JDBC failure chains | F-01, F-02, F-05; partial F-16 |
| 2 — resource ownership | Store registry/lifecycle, global spool quota and bounded MEMORY store | F-03, F-04, F-10, remaining multi-instance F-16 tests |
| 3 — bounded operations and configuration | Keyset/cursor sweepers, execution budgets and schema-aware store configuration | F-06, F-07 |
| 4 — extensibility and maintainability | Deterministic SPI selection, open/closed store type decision, JDBC repository decomposition and bytecode defensive copy | F-08, F-09, F-12, F-13 |
| 5 — release qualification | Remove residual time-based tests, align compiler documentation, complete observability and replay all release gates | F-14, F-15 and cross-cutting release evidence |

## Implemented corrections

### Integration drivers no longer use the affected coordinates

The version catalog now selects:

- PostgreSQL JDBC `42.7.11`, the patched release for
  [CVE-2026-42198](https://github.com/advisories/GHSA-98qh-xjc8-98pq);
- MySQL Connector/J `26.7.0`, outside the `9.7.0-9.7.1` range identified by the
  [July 2026 MySQL advisory](https://dev.mysql.com/community/security/advisories/2026-07-21/);
- Oracle `ojdbc11` `23.26.3.0.0`, after the `23.4.0-23.26.2` range identified by
  the [July 2026 Oracle CPU](https://www.oracle.com/security-alerts/cpujul2026.html).

These dependencies remain restricted to integration-test configurations. The
upgrade reduces CI/release exposure without adding a driver to any published
consumer runtime.

### Store creation is atomic per assembly-line identifier

`AssemblyLineStoreResolver.resolveForPublication(...)` now performs the
fingerprint comparison and replacement through
`ConcurrentHashMap.compute(...)`. Concurrent first lookups for the same
assembly line therefore receive the single store instance installed for that
configuration instead of independently constructing stores and overwriting the
cache.

Phase 2 supersedes this unbounded phase-1 implementation with a bounded,
access-ordered resolver that preserves the same atomic identity guarantee and
adds provider-lease release on replacement, eviction, invalidation and close.

The existing configuration behavior is preserved: a changed fingerprint
replaces the cached store, while an unchanged fingerprint reuses it. Store
capacity, lifecycle and cleanup remain intentionally deferred to phase 2; this
phase does not claim to close F-03.

### JDBC classification traverses chained driver diagnostics

`JdbcPersistenceFailureClassifier` now traverses both `Throwable.getCause()`
and `SQLException.getNextException()` with identity-based cycle detection. It
keeps the previous JSON serialization rejection behavior and uses this SQL
precedence:

1. any retryable SQL diagnostic keeps the batch retryable;
2. otherwise, any proven record-data diagnostic rejects only that record;
3. otherwise, the failure remains systemic.

The rejected-state lookup is also null-safe. A generic outer batch exception
without a SQLState no longer fails with `NullPointerException` before its
chained diagnostics can be inspected.

For SQL-based rejections, `rejectionContext(...)` reports the SQLState, vendor
code and type of the diagnostic that actually established record rejection.
The previous JSON-serialization context behavior is preserved. A generic outer
`BatchUpdateException`/`SQLException` can no longer hide a precise `22001` or
Oracle `12899` diagnostic carried in the next-exception chain.

## Regression coverage

- `AssemblyLineStoreResolverTest` synchronizes sixteen initial callers and
  verifies a single store creation and shared identity.
- The resolver test also preserves replacement on configuration fingerprint
  change and reuse after the replacement.
- `JdbcPersistenceFailureClassifierTest` covers a rejected record in
  `getNextException()`, retryable-over-rejected precedence and a cyclic chained
  diagnostic.
- `PersistenceFailureDispositionTest` exercises the complete batch-bisection
  path and verifies that only the poison record is quarantined with the
  decisive SQL context.

## Validation in the audit environment

The repository Python release-tool tests pass (9/9), and all shell scripts pass
`bash -n`. Both changed production classes compile with Java 17 through the
available `jdk.compiler` module and minimal dependency stubs. Standalone
harnesses exercising the real changed classes reported:

```text
CLASSIFIER_HARNESS_PASS
RESOLVER_HARNESS_PASS creations=2
```

The classifier harness also exposed and qualified the null-SQLState defect
fixed in this phase. The focused Gradle command could not start because the
environment cannot reach the Gradle 9.6.1 distribution host; the wrapper
reported `java.net.SocketException: Network is unreachable`. This is an
environment limitation, not a passing or failing JUnit result. The environment
has no `javac` executable, so compilation was invoked directly through the
equivalent Java 17 compiler module.

Run the authoritative formatting and verification in the connected project
environment:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*AssemblyLineStoreResolverTest'
./gradlew :gear4jtest-jdbc:test \
  --tests '*JdbcPersistenceFailureClassifierTest' \
  --tests '*PersistenceFailureDispositionTest'
./gradlew check
```

After the driver upgrades, replay the complete database matrix and SCA gate:

```bash
./gradlew integrationTest dependencyCheckAggregate
```

The Gradle/CI result is required before merging or releasing this phase.

## Explicit non-goals and residual risks

- The store cache is still unbounded and `ArtifactStore` still has no explicit
  close/ownership contract; phase 2 introduces the shared bounded registry.
- The spool quota is still per manager instance; phase 2 owns the shared-path
  accounting and lease design.
- Dependency locking and Gradle verification metadata remain explicitly
  deferred until after 1.0 by the established project policy; they are outside
  this pre-release remediation roadmap.
- Reconciler/checker pagination and total-work budgets are unchanged until
  phase 3.
- No public Java API or database migration changes in this phase.
