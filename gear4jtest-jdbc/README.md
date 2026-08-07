# gear4jtest-jdbc

`gear4jtest-jdbc` contains the optional JDBC persistence implementation for Gear4J.

It depends on `gear4jtest-core` contracts and owns the storage-specific pieces that must not live in core:

- `DatabaseExecutionManager` for JDBC-backed `RunPersistenceManager` execution persistence;
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
        .objectMapper(applicationObjectMapper)
        .payloadCloner(JacksonPayloadCloners.with(applicationObjectMapper))
        .autoCreateTables(true)
        .build();
```

Repository writes use autonomous transactions by default. Each write obtains a
fresh connection with `autoCommit=true`, owns its commit/rollback and closes the
connection. A connection already participating in an ambient transaction is
rejected before SQL runs, so Gear4J cannot accidentally commit or roll back
caller-owned work.

Framework integrations can provide `JdbcTransactionOperations` to the
`DatabaseExecutionManager` or `DatabaseAssemblyRunRepository` builder. The
transaction implementation then owns the complete boundary; repository code
does not call `commit`, `rollback` or change auto-commit itself. The Spring Boot
starter automatically selects its `REQUIRES_NEW` adapter when a
`DataSourceTransactionManager` is available.

Supplying the application `ObjectMapper` preserves its Java time modules, custom
serializers and business-type configuration for persisted JSON values. For a
different JSON strategy, implement `PersistenceJsonCodec` and provide it with
`.jsonCodec(...)`; the most recently selected codec or mapper is used.

Persistence captures apply redaction first, then isolate every retained value
before it can enter the asynchronous station-log buffer. Maps, collections,
optionals and arrays are recursively copied. Unknown mutable business types
require an explicit `PayloadCloner`; `JacksonPayloadCloners.with(...)` is the
recommended choice when the application already uses the optional
`gear4jtest-jackson` module. The default strict cloner accepts only known
immutable leaf values and fails instead of retaining an unsafe reference.

The legacy `.flushThreshold(...)` convenience only overrides the effective
batch size. It preserves every other value from
`PersistenceRuntimeConfiguration`, regardless of whether it is called before or
after `.configuration(...)`. If necessary, the per-run pending-log limit is
raised to remain greater than or equal to the batch size.

Each run can use a smaller threshold through
`PersistenceConfiguration.stationLogFlushThreshold(...)`. A `RunRequest`
persistence override takes precedence over the assembly-line configuration.
The threshold is retained in the run-local buffer and therefore applies
consistently to asynchronous, periodic, final and shutdown drains. Values above
the manager's bounded `maxPendingLogsPerRun` capacity are rejected before the run
record is written.

Auto-migration does not silently adopt a pre-existing Gear4J schema that has no
`gear4j_schema_history` entry. For a verified compatible schema, adoption must
be explicitly enabled with `.baselineOnMigrate(true)`; the migrator then checks
all expected V1 tables, columns and named indexes before writing history.
Managed migrations persist `STARTED`, `APPLIED` and `FAILED` states. Gear4J
refuses to retry an incomplete migration automatically because some databases
can commit DDL statement by statement. Follow the
[partial-migration recovery runbook](../docs/architecture/jdbc-migrations.md#partial-migration-recovery-runbook)
before calling `JdbcSchemaMigrator.prepareRetry(...)`.

For an observable shutdown, call `manager.shutdownWithReport(timeout)`. The
returned `PersistenceShutdownReport` states how many station logs were drained,
which runs still retain data, how many retry attempts occurred, whether the
deadline was reached, whether an earlier run finalization remains unresolved and
whether owned flush workers terminated, and how many normal operations admitted
before closure were still running. The existing `shutdown()` methods remain
available and delegate to the same bounded retry workflow. Retry backoff defaults
to `100ms` and grows exponentially up to `2s`; both values are configurable
through `PersistenceRuntimeConfiguration`.

Normal persistence operations use a short admission gate only while checking
lifecycle state and updating an in-flight counter. JDBC calls are not executed
under a manager-wide lock, so independent runs may write concurrently according
to the application threads and configured flush executor. Per-run buffer drains
remain serialized by their own lock. Custom repositories and data sources supplied
to the builder must be thread-safe. Shutdown closes admission first and waits for already admitted operations only
until the shared end-to-end deadline. If they remain active, the report exposes
`unfinishedOperations` and the drain is not started concurrently with them.

Failed batches remain in memory and are listed in the report; no durable
dead-letter store is enabled implicitly. The shutdown deadline starts before admission closure and bounds operation waits,
per-run locks, retries, backoff and worker termination. Shutdown-only JDBC calls
run on daemon workers, so a pool or driver that ignores interruption may outlive
the report without blocking the caller. In that case the drained batch is
restored, `deadlineReached` is true and `flushExecutorTerminated` is false.
