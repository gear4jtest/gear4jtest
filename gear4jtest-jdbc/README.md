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
