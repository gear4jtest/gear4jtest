# Phase 4 — Persistence, API boundaries and dependency scopes

**Date:** 2026-08-14

**Scope:** external publication tags, JDBC publication writes, generated-source
compiler boundaries, the experimental-cache Jackson contract and the staged
consumer compilation probe.

## Confirmed findings

### Publication tags were only length-bounded

`AssemblyLinePublicationService`, `OperationChainPublicationStage` and
`OperationChainObjectRepositoryJdbc` each implemented their own tag
normalization. They agreed on the database column length of 100 characters but
did not bound the number of entries in one request. The duplicate rules could
therefore drift, and a large caller-owned list caused unbounded validation and
JDBC work before the database rejected anything.

Direct `OperationChainTagRepository` implementations were also inconsistent:
the JDBC implementation delegated blank/oversized values to the database while
the in-memory implementation accepted blanks and values longer than the schema.

### JDBC publication performed one command per tag

`OperationChainObjectRepositoryJdbc.insertStageTags(...)` created one savepoint
and one prepared statement for every stage tag. `insertTags(...)` reused its
statement but still called `executeUpdate()` once per committed tag. On a remote
database, latency therefore grew linearly with the number of tags. Retrying an
existing stage required duplicate-key exception control flow.

### A public experimental-cache constructor leaked an implementation dependency

`JsonSha256FingerprintStrategy(ObjectMapper)` is public, but
`gear4jtest-experimental-cache/build.gradle` declared Jackson as
`implementation`. A consumer compiling against the published module could not
use that constructor without discovering and redeclaring `jackson-databind`.
The constructor also accepted `null` and failed only on the first fingerprint.

### The concrete JDT adapter appeared to be stable SPI

The compiler package is marked `@Spi`, so the public
`JDTInMemoryCompiler` looked like a supported implementation type even though
it imports multiple `org.eclipse.jdt.internal.compiler.*` packages. The stable
factories already returned `GeneratedSourceCompiler`; the concrete adapter did
not need to be a consumer compatibility commitment. One test also imported an
internal JDT constant type directly, spreading the coupling.

The low-level in-memory adapter still necessarily uses Eclipse implementation
packages. This phase confines that accepted dependency; it does not pretend the
underlying API has become stable.

## Implemented corrections

- Added internal provider helper `OperationChainPublicationTags` as the single
  tag policy: at most 64 entries per publication request, each non-blank and at
  most 100 characters, with deterministic deduplication and sorting.
- Applied the same validation before artifact validation/storage, in staged
  records, in-memory publication, JDBC publication and direct in-memory/JDBC tag
  operations. `null` publication lists retain their historical empty-list
  meaning.
- Bounded the merged tag set of an idempotently renewed stage to 64. JDBC locks
  the existing stage row before checking and merging tags, preventing concurrent
  retries from independently passing the bound and persisting a larger union.
- Added dialect-specific idempotent stage-tag SQL for PostgreSQL,
  MySQL/MariaDB, Oracle and H2.
- Replaced per-tag stage savepoints/statements and committed-tag
  `executeUpdate()` loops with one prepared `addBatch()/executeBatch()` per tag
  set. Transaction ownership and atomic object/tag publication are unchanged.
- Changed the experimental-cache Jackson dependency from `implementation` to
  `api`, aligned with the public `ObjectMapper` constructor, and rejected a null
  mapper at construction time.
- Added an isolated `experimentalCacheScope` source set to the staged consumer
  build. It declares only the published experimental-cache artifact and compiles
  a constructor call using `ObjectMapper`, so a future incorrect POM scope fails
  the release consumer gate even if other modules expose Jackson correctly.
- Marked `JDTInMemoryCompiler` as `@Internal` and final, documented the stable
  factory/SPI boundary, removed the internal JDT import from its test, and added
  an architecture guard that permits `org.eclipse.jdt.internal.*` imports only
  in that adapter.
- Removed the three divergent tag-normalization implementations in favor of the
  shared policy.

## Compatibility and operational impact

The 64-entry publication limit is an intentional pre-1.0 safety change.
Callers publishing more tags must reduce their publication taxonomy before
upgrading. Existing database rows are not rewritten. The limit applies to a
single staged publication (including the union created by its retries); the
searchable tags accumulated for an assembly line across distinct publications
remain a database-level data-governance concern.

No schema migration is required: the 100-character limit already matches the
existing stage-tag and committed-tag columns. SQL batching stays inside the
same transaction and uses the same uniqueness keys, so idempotence and rollback
semantics remain intact.

`GeneratedSourceCompilers.jdt(...)` remains available and returns the stable
`GeneratedSourceCompiler` interface. Consumer code that directly instantiated
`JDTInMemoryCompiler` was relying on an implementation type and should move to
the factory before 1.0.

## Regression coverage

- null, duplicate, unsorted, blank, null-element and overlength publication
  tags;
- rejection of 65 input tags before translator/compiler/store interaction;
- direct in-memory and JDBC tag operations applying the schema rules;
- all four supported dialect families producing idempotent stage-tag SQL;
- existing H2 publication/retry/rollback integration paths now exercising the
  batched implementation;
- deterministic JSON SHA-256 fingerprints and eager rejection of a null custom
  mapper;
- isolated staged-consumer compilation of the Jackson-exposing experimental
  constructor;
- source architecture guard for confinement of Eclipse compiler internals.

## Validation performed in the audit environment

- all `gear4jtest-core` production sources compiled with Java 17;
- all `gear4jtest-jdbc` and `gear4jtest-external-jdbc` production sources
  compiled with Java 17 and the available Jackson/SLF4J API jars;
- all `gear4jtest-experimental-cache` production sources compiled with Java 17;
- all `gear4jtest-external-api` production sources except the concrete JDT
  adapter/factory trio compiled with Java 17; a small factory stub represented
  the unavailable Eclipse JDT artifact;
- the new focused tag and fingerprint test sources compiled against minimal
  API-compatible JUnit/AssertJ stubs;
- an executable behavior harness passed for tag bounds, all stage-tag SQL
  dialects and deterministic JSON SHA-256 output;
- `PublishedApiBoundaryAnalyzer` ran over every published module and reported
  no internal-type signature violation;
- compiled JDBC bytecode contains one `addBatch/executeBatch` pair for stage
  tags and one for committed tags; only the object/stage conflict paths retain a
  savepoint.

The Gradle wrapper could not download Gradle 9.6.1 because this environment has
no route to `services.gradle.org`. The authoritative formatter and tests must be
run in the normal development environment:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*OperationChainPublicationTagsTest' \
  --tests '*InMemoryOperationChainRepositoryTest' \
  --tests '*AssemblyLineManagerTest' \
  --tests '*GeneratedSourceCompilersTest'
./gradlew :gear4jtest-external-jdbc:test \
  --tests '*ExternalRepositorySqlDialectCoverageTest' \
  --tests '*OperationChainTagRepositoryJdbcBehaviorTest'
./gradlew :gear4jtest-external-jdbc:integrationTest \
  --tests '*OperationChainPublicationRepositoryJdbcIT'
./gradlew :gear4jtest-experimental-cache:test \
  --tests '*JsonSha256FingerprintStrategyTest'
./gradlew consumerSmokeTest
./gradlew check
```

## Explicit non-goals and residual risks

- Eclipse JDT's low-level in-memory hooks remain internal upstream APIs. The
  dependency is now isolated and explicitly non-stable; replacing it with a
  public upstream in-memory API is not currently possible without changing the
  backend or compilation model.
- This phase does not add dependency locking, Gradle verification metadata or
  mandatory lockfile CI enforcement. Those supply-chain controls remain
  explicitly deferred until after 1.0.
- It does not add a schema-wide maximum number of tags accumulated for one
  assembly-line identifier across multiple versions. If that cardinality needs
  a hard tenant quota, it requires a separate transactional/schema decision.
