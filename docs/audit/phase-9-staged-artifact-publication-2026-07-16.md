# Phase 9 — Staged artifact publication and boundary validation

## Scope

This phase addresses audit findings A07, A15, A21 and A24: publication inputs were insufficiently aligned with the database
schema, XML streams were bounded only after schema work, artifact storage preceded compilation and durable metadata, and
artifact-store configuration could silently degrade requested guarantees.

## Implemented changes

### Publication order and recovery

- TEST and direct RUN bytes are translated and compiled before artifact storage.
- `OperationChainPublicationRepository` now exposes a staged lifecycle and `AssemblyLineManager` requires it.
- The exact artifact-store configuration is resolved before stage creation and represented by a stable SHA-256
  fingerprint.
- Publication stages contain the candidate object, normalized tags, store fingerprint, age and revision but remain
  invisible to ordinary object and tag queries.
- Artifact storage occurs only after the stage is durable; metadata becomes visible only after an atomic commit.
- Ambiguous store failures and metadata-commit failures retain the stage for reconciliation.
- Store-resolution failures happen before a stage exists and before any artifact operation.
- `ArtifactPublicationReconciler` commits stages whose expected hash exists, conditionally aborts unchanged stages whose
  artifact is missing and retains stages when checking fails.
- An idempotent retry renews the stage timestamp and increments its revision. Reconciliation aborts only the exact revision
  it observed, so a concurrent retry cannot lose its stage.
- A store-configuration mismatch retains the stage and does not probe the newly configured backend.
- Reconciliation reports both `retained()` stages and `fullyReconciled()` so a technically successful pass cannot be
  mistaken for a pass that resolved every stage.

### Repository implementations

- `InMemoryOperationChainRepository` implements staging, commit, abort and paged stage lookup under one monitor.
- `OperationChainObjectRepositoryJdbc` persists stages and tags transactionally on H2, PostgreSQL, MySQL, MariaDB and
  Oracle.
- Five V1 migration variants add publication-stage and stage-tag tables plus an age index.
- JDBC stage creation is idempotent by the publication natural key; retries renew the stage; commit, abort and conditional
  abort are idempotent.

### Boundary validation

- `OperationChainObject` validates database lengths, required values, SHA-256 format and non-negative sizes.
- `OperationChainConfig` validates its identifier, policy, store type and immutable property map.
- Tags are normalized, deduplicated and rejected when blank or longer than 100 characters.
- Artifact-store booleans accept only `true` or `false`; incomplete or invalid fallback groups fail startup.
- Replication and self-healing cannot be enabled without a complete fallback.
- `ArtifactStoreResolver.availableTypes()` returns an immutable snapshot.
- `AssemblyLineValidator` applies the same configurable byte limit to arrays and streams before XSD validation.

## Failure semantics

The protocol deliberately does not delete a blob after an ambiguous `put` failure. A content-addressed object may be shared,
and the exception does not prove that the provider did not commit the bytes. The durable stage lets reconciliation decide by
checking the expected hash after a grace period.

The reconciler does not assume that the current configuration still points to the backend used for the upload. The stage
stores a configuration fingerprint; a mismatch is reported and retained for explicit operator handling.

This guarantee applies to new writes performed through `AssemblyLineManager`. The generic store SPI still cannot enumerate
legacy or out-of-band store-only artifacts.

## Compatibility

- No public method was removed.
- Custom publication repositories used by the manager must now return `supportsStaging() == true` and implement stage
  renewal, commit, `abortIfUnchanged` and paged staged lookup.
- Model constructors reject values that would otherwise fail later against the V1 schema.
- Boolean aliases such as `yes` and `1` are no longer accepted in artifact-store configuration.
- V1 migrations were edited directly because the project is pre-production. Existing persistent development schemas must
  be recreated or migrated manually.

## Validation performed without Gradle

- Java 17 compilation of all modified external API foundation classes and the manager facade.
- Java 17 compilation of the publication service and manager with minimal unrelated dependency stubs.
- Java 17 compilation of the real JDBC repository and SQL dialect support.
- Java 17 compilation of the XML validator.
- Autonomous staging/reconciliation smoke test covering invisible stage, commit on present content, abort on missing
  content, retry renewal and protection from stale abort.
- Autonomous provider smoke test covering stable store fingerprints, strict booleans, fallback requirements and immutable
  provider-type snapshots.
- Autonomous XML boundary smoke test proving that an oversized stream is rejected after reading only `maxXmlBytes + 1`.
- Static contract check for publication-stage tables, store fingerprint, revision, age index and cascading stage tags on
  all five SQL migration variants.
- Static checks for all five migration variants, Markdown links, YAML syntax and archive hygiene.

The complete Gradle and Testcontainers suites remain the authoritative connected validation.
