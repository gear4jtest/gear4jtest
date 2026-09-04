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
        .maxActiveRuns(1_000)
        .maxBufferedStationLogs(10_000)
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

The manager also enforces process-wide limits through `maxActiveRuns` and
`maxBufferedStationLogs`. A station log continues to consume global capacity
while its JDBC write is in flight; capacity is released only after a successful
commit or after the record has been accepted by the configured rejection
handler. Reaching either limit fails the admitting call synchronously instead of
allocating another unbounded per-run queue.

Record-local data failures are handled separately from transient and systemic
database failures. After a failed batch is positively classified as a rejected
record (for example SQLState `22001`), Gear4J bisects that batch, commits healthy
subsets in independent transactions and invokes `RejectedPersistenceRecordHandler`
for each isolated bad record. Unknown constraint failures and infrastructure
failures are never guessed to be record-local: the unresolved records are put
back in their original order for retry. If the rejection handler itself fails,
the rejected record is also restored and the flush fails.

The default rejection handler writes only safe identifiers and bounded failure
metadata to the log; it is not a durable dead-letter store. Applications that
require zero-loss accounting must provide a durable handler:

```java
DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .rejectedPersistenceRecordHandler((record, failure) -> durableSink.store(record, failure))
        .build();
```

Auto-migration does not silently adopt a pre-existing Gear4J schema that has no
`gear4j_schema_history` entry. For a verified compatible schema, adoption must
be explicitly enabled with `.baselineOnMigrate(true)`; the migrator then checks
all expected V1 tables, columns and named indexes before writing history.
Managed migrations persist `STARTED`, `APPLIED` and `FAILED` states. Gear4J
refuses to retry an incomplete migration automatically because some databases
can commit DDL statement by statement. Follow the
[partial-migration recovery runbook](../docs/architecture/jdbc-migrations.md#partial-migration-recovery-runbook)
before calling `JdbcSchemaMigrator.prepareRetry(...)`.

Execution-history pagination is backed by composite indexes whose final columns
match `ORDER BY start_time, id`. The integration matrix seeds 20,000 runs and
10,000 station logs, verifies the ordered index definitions independently from
optimizer choice, and writes p50/p95/max plus natural-plan evidence under
`build/reports/sql-plan-qualification`. Each report states whether the reference
index was selected and whether a full scan was observed. PostgreSQL, MySQL,
MariaDB and Oracle are qualified; H2 remains a local functional dialect. The
timing ceiling is a catastrophic-regression guardrail, not a production SLO.

The corrected indexes are part of V1 because Gear4J has no production adopters.
Recreate older development schemas before using this source version; do not
baseline an old V1 schema unless it contains the complete current index set.

For an observable shutdown, call `manager.shutdownWithReport(timeout)`. The
returned `PersistenceShutdownReport` states how many station logs were drained,
which runs still retain data, how many retry attempts occurred, whether the
deadline was reached, whether an earlier run finalization remains unresolved and
whether the regular flush executor is caller-owned, terminated or still running,
whether the shutdown-only JDBC executor terminated, and how many normal operations
admitted before closure were still running. The existing `shutdown()` methods remain
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

Failed unresolved batches and failed final run updates remain in memory for
retry and are listed in the report. Periodic maintenance retries a pending final
update even when the caller does not invoke `end(...)` again, and `flush(runId)`
can trigger the same retry explicitly. The run-buffer
permit is released only when both its station logs and final update have
completed. No durable dead-letter store is enabled implicitly. The shutdown
deadline starts before admission closure and bounds operation waits, per-run
locks, retries, backoff, rejection-handler calls and worker termination.
Shutdown-only JDBC and rejection-handler calls run on daemon workers, so a pool,
driver or application handler that ignores interruption may outlive the report
without blocking the caller. In that case the drained batch is restored,
`deadlineReached` is true and `shutdownJdbcExecutorTerminated` is false.
