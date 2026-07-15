# gear4jtest-jdbc

`gear4jtest-jdbc` contains the optional JDBC persistence implementation for Gear4J.

It depends on `gear4jtest-core` contracts and owns the storage-specific pieces that must not live in core:

- `DatabaseExecutionManager` for JDBC-backed `AssemblyRunManager` execution persistence;
- `DatabaseAssemblyRunRepository` for persisted run and station-log reads;
- `Gear4jDatabaseDialect` for explicit dialect selection;
- `JdbcSchemaMigrator` and SQL resources for Gear4J-managed schemas;
- Jackson-backed JSON mapping for persisted payload/context/result columns.

Applications must explicitly select a `Gear4jDatabaseDialect`; Gear4J does not infer dialects from JDBC metadata.
Use a pooled `DataSource` in production and provide a `SensitiveDataRedactor` when persisted values can contain secrets
or personal data.

Runtime schema creation is disabled by default for both direct and Spring Boot usage. Enable it explicitly only when
Gear4J is intended to own its schema lifecycle.

```java
var persistenceRuntime = PersistenceRuntimeConfiguration.builder()
        .batchSize(500)
        .maxPendingLogsPerRun(10_000)
        .flushInterval(Duration.ofSeconds(1))
        .build();

DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .configuration(persistenceRuntime)
        .autoCreateTables(true)
        .build();
```

Auto-migration does not silently adopt a pre-existing Gear4J schema that has no
`gear4j_schema_history` entry. For a verified compatible schema, adoption must
be explicitly enabled with `.baselineOnMigrate(true)`; the migrator then checks
all expected V1 tables, columns and named indexes before writing history.

For an observable shutdown, call `manager.shutdownWithReport(timeout)`. The
returned `PersistenceShutdownReport` states how many station logs were drained,
which runs still retain data, how many retry attempts occurred, whether the
deadline was reached, whether an earlier run finalization remains unresolved and
whether owned flush workers terminated. The existing `shutdown()` methods remain
available and delegate to the same bounded retry workflow. Retry backoff defaults
to `100ms` and grows exponentially up to `2s`; both values are configurable
through `PersistenceRuntimeConfiguration`.

Normal persistence operations use a short admission gate only while checking
lifecycle state and updating an in-flight counter. JDBC calls are not executed
under a manager-wide lock, so independent runs may write concurrently according
to the application threads and configured flush executor. Per-run buffer drains
remain serialized by their own lock. Custom repositories and data sources supplied
to the builder must be thread-safe. Shutdown closes admission first and waits for
already admitted operations before taking its drain snapshot.

Failed batches remain in memory and are listed in the report; no durable
dead-letter store is enabled implicitly. The shutdown deadline bounds retries,
backoff and executor termination. A JDBC call already in progress still depends
on the configured `jdbcStatementTimeout` and on the driver honoring interruption
and query timeouts.
