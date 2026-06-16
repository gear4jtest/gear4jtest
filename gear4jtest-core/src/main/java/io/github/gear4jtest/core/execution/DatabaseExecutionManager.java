package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed run manager with bounded station-log buffering and asynchronous
 * batched flushes.
 */
public class DatabaseExecutionManager implements AssemblyRunManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutionManager.class);
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final OperationRecordBufferRegistry buffers;
    private final PersistenceFlushCoordinator flushCoordinator;
    private final SensitiveDataRedactor redactor;

    public DatabaseExecutionManager(DataSource dataSource, Gear4jDatabaseDialect databaseDialect) {
        this(dataSource, databaseDialect, PersistenceRuntimeConfiguration.defaults(), true,
                SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    int flushThreshold,
                                    boolean autoCreateTables) {
        this(dataSource, databaseDialect,
                PersistenceRuntimeConfiguration.builder().batchSize(flushThreshold)
                        .maxPendingLogsPerRun(Math.max(flushThreshold, 10_000)).build(),
                autoCreateTables, SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables) {
        this(dataSource, databaseDialect, configuration, autoCreateTables, SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables,
                                    SensitiveDataRedactor redactor) {
        this(new DatabaseAssemblyRunRepository(Objects.requireNonNull(dataSource, "dataSource must not be null"),
                Objects.requireNonNull(databaseDialect, "databaseDialect must not be null")), configuration,
                autoCreateTables, PersistenceFlushCoordinator.createFlushExecutor(configuration),
                Executors.newSingleThreadScheduledExecutor(PersistenceThreadFactories.maintenance()), true, true,
                redactor);
    }

    /**
     * Compatibility constructor for caller-managed flush executors. The supplied
     * executor is never shut down by this manager.
     */
    public DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                    int flushThreshold,
                                    boolean autoCreateTables,
                                    ExecutorService flushExecutor) {
        this(repository,
                PersistenceRuntimeConfiguration.builder().batchSize(flushThreshold)
                        .maxPendingLogsPerRun(Math.max(flushThreshold, 10_000)).build(),
                autoCreateTables, flushExecutor,
                Executors.newSingleThreadScheduledExecutor(PersistenceThreadFactories.maintenance()), false, true,
                SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables,
                                    ExecutorService flushExecutor,
                                    ScheduledExecutorService maintenanceExecutor) {
        this(repository, configuration, autoCreateTables, flushExecutor, maintenanceExecutor, false, false,
                SensitiveDataRedactor.none());
    }

    private DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                     PersistenceRuntimeConfiguration configuration,
                                     boolean autoCreateTables,
                                     ExecutorService flushExecutor,
                                     ScheduledExecutorService maintenanceExecutor,
                                     boolean ownsFlushExecutor,
                                     boolean ownsMaintenanceExecutor,
                                     SensitiveDataRedactor redactor) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.buffers = new OperationRecordBufferRegistry(configuration.maxPendingLogsPerRun());
        this.redactor = redactor != null ? redactor : SensitiveDataRedactor.none();
        if (SensitiveDataRedactor.isNone(this.redactor)) {
            LOGGER.warn("[Gear4J] JDBC persistence is enabled with no SensitiveDataRedactor. "
                    + "Pipeline payloads, contexts and results will be persisted as-is.");
        }
        if (autoCreateTables) {
            this.repository.initialize();
        }
        this.flushCoordinator = new PersistenceFlushCoordinator(this.repository, this.configuration, buffers,
                flushExecutor, maintenanceExecutor, ownsFlushExecutor, ownsMaintenanceExecutor);
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        flushCoordinator.ensureOpen();
        repository.save(AssemblyRunRecord.from(execution, redactor));
        buffers.createFresh(execution.getId());
    }

    @Override
    public void append(StationLogRecord record) {
        if (record == null) {
            return;
        }
        flushCoordinator.ensureOpen();
        record = record.redactedWith(redactor);
        UUID runId = record.pipelineExecutionId();
        OperationRecordBuffer buffer = buffers.getOrCreate(runId);
        boolean shouldScheduleFlush = buffer.append(record, configuration.batchSize(),
                                                    flushCoordinator.counters());
        if (shouldScheduleFlush) {
            flushCoordinator.scheduleAsyncFlush(buffer, false);
        }
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    @Override
    public void flush(UUID pipelineId) {
        if (pipelineId == null) {
            return;
        }
        OperationRecordBuffer buffer = buffers.get(pipelineId);
        if (buffer != null) {
            buffer.assertHealthy();
            flushCoordinator.flushBufferBlocking(buffer, false);
            buffer.assertHealthy();
        }
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        UUID runId = finalExecution.getId();
        OperationRecordBuffer buffer = buffers.getOrCreate(runId);
        buffer.close();
        boolean completed = false;
        try {
            buffer.assertHealthy();
            flushCoordinator.flushBufferBlocking(buffer, true);
            buffer.assertHealthy();
            repository.update(AssemblyRunRecord.from(finalExecution, redactor));
            completed = true;
        } finally {
            if (completed) {
                buffers.remove(runId);
            }
        }
    }

    public PersistenceRuntimeStats snapshotStats() {
        return flushCoordinator.snapshotStats();
    }

    @Override
    public void shutdown() {
        shutdown(configuration.shutdownTimeout());
    }

    public void shutdown(Duration timeout) {
        flushCoordinator.shutdown(timeout);
    }
}
