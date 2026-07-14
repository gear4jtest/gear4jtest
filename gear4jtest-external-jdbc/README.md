# gear4jtest-external-jdbc

Optional JDBC persistence for external assembly-line definitions. The module depends on `gear4jtest-external-api`,
`gear4jtest-jdbc` and Jackson; applications that use only memory or filesystem artifacts do not pull those dependencies.

## Coordinates and migration from pre-1.0 packages

```groovy
implementation "io.github.gear4jtest:gear4jtest-external-jdbc:${gear4jVersion}"
```

Phase 7 moved provider implementations before the 1.0 compatibility baseline:

| Pre-1.0 package | 1.0 package |
| --- | --- |
| `io.github.gear4jtest.external.api.repository.jdbc.*` | `io.github.gear4jtest.external.jdbc.repository.*` |
| `io.github.gear4jtest.external.api.artifact.DatabaseArtifactStore` | `io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore` |
| `io.github.gear4jtest.external.api.spi.DatabaseArtifactStorePlugin` | `io.github.gear4jtest.external.jdbc.spi.DatabaseArtifactStorePlugin` |

The `DATABASE` artifact-store plugin remains discoverable through `ServiceLoader` when this module is on the runtime
classpath. Memory and filesystem plugins remain in `gear4jtest-external-api`.

## Supported databases

PostgreSQL, MySQL, MariaDB and Oracle are supported in production. H2 is supported for local and integration testing.
Every entry point requires an explicit `Gear4jDatabaseDialect`; no JDBC metadata fallback selects a dialect silently.

```java
OperationChainTagRepositoryJdbc tags = OperationChainTagRepositoryJdbc.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .build();

DatabaseArtifactStore artifacts = DatabaseArtifactStore.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .maxArtifactSizeBytes(5L * 1024L * 1024L)
        .spoolPolicy(ArtifactSpoolPolicy.builder()
                .directory(Path.of("/var/lib/my-app/gear4j-spool"))
                .maxBytes(100L * 1024L * 1024L)
                .staleFileAge(Duration.ofHours(24))
                .build())
        .build();
```

## Schema and transaction contract

`ExternalJdbcSchemaMigrator.forDialect(dialect)` rejects an existing schema without Gear4J migration history. After an
operator has verified a compatible V1 schema, `forDialect(dialect, true)` validates all required tables, columns and named
indexes before recording the explicit baseline.

`OperationChainObjectRepositoryJdbc` implements `OperationChainPublicationRepository`. Object metadata and tags are
published atomically on the repository's `DataSource`; the object and tag repositories must target the same schema.
Repeated publication of identical `(al_id, version, mode)` content is idempotent and conflicting content is rejected.

## Database artifact streaming

`DatabaseArtifactStore#get` loads only bounded metadata. `Artifact#openStreamChecked()` then owns a fresh connection,
statement, result set and binary stream until the caller closes it. Always use try-with-resources. Writes spool to a
private bounded directory before hashing and insertion; the default artifact limit is 5 MiB and the default aggregate
spool quota is 100 MiB.

The `DATABASE` plugin accepts:

| Property | Default | Purpose |
| --- | --- | --- |
| `dialect` | required | Explicit `Gear4jDatabaseDialect`. |
| `datasource` | `datasource.default` | Lookup key containing the `DataSource`. |
| `table` | `artifact_store` | Validated simple SQL table identifier. |
| `maxArtifactSizeBytes` | `5242880` | Maximum stored/read size; `-1` explicitly disables the bound. |
| `spoolDirectory` | private temporary directory | Staging directory for hash-then-insert writes. |
| `spoolMaxBytes` | `104857600` | Aggregate temporary-byte quota. |
| `spoolStaleFileAge` | `PT24H` | Age after which stale `.tmp` files are removed on initialization. |

## Verification

```bash
./gradlew :gear4jtest-external-jdbc:test
./gradlew :gear4jtest-external-jdbc:integrationTest
```
