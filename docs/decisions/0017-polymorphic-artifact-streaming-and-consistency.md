# ADR 0017 — Stream database artifacts and check polymorphic references operationally

## Status

Accepted.

## Context

`DatabaseArtifactStore#get` previously used `ResultSet#getBytes`, allocating the
whole BLOB before returning an `Artifact`. Direct database-store writes also had
no store-level default limit, and temporary files were created in the implicit
JVM temporary directory.

`operation_chain_object.content_hash` cannot safely reference
`artifact_store.hash_hex` with a global foreign key. The configured artifact
backend is polymorphic and may be DATABASE, FILESYSTEM, S3, SFTP or MEMORY.

## Decision

- Database writes use a configurable private spool directory so the SHA-256 hash
  is known before the content-addressed insert.
- Direct byte-array writes, streamed writes and reads use a 5 MiB default limit.
  Unlimited mode requires the explicit value `-1`.
- Database reads fetch bounded metadata first. Each stream open issues a fresh
  query and returns `ResultSet#getBinaryStream` wrapped in an input stream that
  owns and closes the JDBC result set, statement and connection.
- The wrapper verifies the declared size while reading, counts early closes and
  exposes cumulative size, latency and failure statistics through
  `ArtifactStoreMonitor`.
- No cross-backend foreign key is added. `ArtifactConsistencyChecker` pages
  operation-chain metadata, deduplicates hashes and compares it with the store
  selected by the assembly-line configuration.
- No migration V2 is introduced. The pre-1.0 V1 schemas remain the source of
  truth and require no structural change for this decision.

## Consequences

- Callers must close database artifact streams promptly. Internal Gear4J reads
  use `openStreamChecked()` in try-with-resources.
- A stream may hold one pool connection for its lifetime; early-close metrics
  make misuse observable.
- JDBC drivers may still internally buffer `BYTEA`/BLOB data, but Gear4J no
  longer explicitly allocates the entire content with `getBytes`.
- Referential inconsistencies are detected by an explicit operational check
  rather than rejected by a database constraint that would be incorrect for
  non-database stores.
