package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed run manager with bounded station-log buffering and asynchronous
 * batched flushes.
 */
public class DatabaseExecutionManager implements AssemblyRunManager, PersistenceRuntimeMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutionManager.class);
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final OperationRecordBufferRegistry buffers;
    private final PersistenceFlushCoordinator flushCoordinator;
    private final SensitiveDataRedactor redactor;

    public static Builder builder() {
        return new Builder();
    }

    private DatabaseExecutionManager(Builder builder) {
        this.configuration = Objects.requireNonNull(builder.configuration, "configuration must not be null");
        this.repository = builder.repository != null
                ? builder.repository
                : createRepository(builder.dataSource, builder.databaseDialect, this.configuration);
        this.buffers = new OperationRecordBufferRegistry(configuration.maxPendingLogsPerRun());
        this.redactor = builder.redactor != null ? builder.redactor : SensitiveDataRedactor.none();
        if (SensitiveDataRedactor.isNone(this.redactor)) {
            LOGGER.warn("[Gear4J] JDBC persistence is enabled with no SensitiveDataRedactor. "
                    + "Assembly line payloads, contexts and results will be persisted as-is.");
        }
        if (builder.autoCreateTables) {
            this.repository.initialize();
        }

        ExecutorService flushExecutor = builder.flushExecutor != null
                ? builder.flushExecutor
                : PersistenceFlushCoordinator.createFlushExecutor(configuration);
        ScheduledExecutorService maintenanceExecutor = builder.maintenanceExecutor != null
                ? builder.maintenanceExecutor
                : Executors.newSingleThreadScheduledExecutor(PersistenceThreadFactories.maintenance());
        boolean ownsFlushExecutor = builder.flushExecutor == null || builder.ownsFlushExecutor;
        boolean ownsMaintenanceExecutor = builder.maintenanceExecutor == null || builder.ownsMaintenanceExecutor;
        this.flushCoordinator = new PersistenceFlushCoordinator(this.repository, this.configuration, buffers,
                flushExecutor, maintenanceExecutor, ownsFlushExecutor, ownsMaintenanceExecutor);
    }

    private static DatabaseAssemblyRunRepository createRepository(DataSource dataSource,
                                                                  Gear4jDatabaseDialect databaseDialect,
                                                                  PersistenceRuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return DatabaseAssemblyRunRepository.builder()
                .dataSource(Objects.requireNonNull(dataSource, "dataSource must not be null"))
                .databaseDialect(Objects.requireNonNull(databaseDialect, "databaseDialect must not be null"))
                .objectMapper(new ObjectMapper())
                .jdbcStatementTimeout(configuration.jdbcStatementTimeout())
                .build();
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private DatabaseAssemblyRunRepository repository;
        private PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.defaults();
        private boolean autoCreateTables = true;
        private ExecutorService flushExecutor;
        private ScheduledExecutorService maintenanceExecutor;
        private boolean ownsFlushExecutor;
        private boolean ownsMaintenanceExecutor;
        private SensitiveDataRedactor redactor = SensitiveDataRedactor.none();

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder databaseDialect(Gear4jDatabaseDialect databaseDialect) {
            this.databaseDialect = databaseDialect;
            return this;
        }

        public Builder repository(DatabaseAssemblyRunRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder configuration(PersistenceRuntimeConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder flushThreshold(int flushThreshold) {
            this.configuration = PersistenceRuntimeConfiguration.builder()
                    .batchSize(flushThreshold)
                    .maxPendingLogsPerRun(Math.max(flushThreshold, 10_000))
                    .build();
            return this;
        }

        public Builder autoCreateTables(boolean autoCreateTables) {
            this.autoCreateTables = autoCreateTables;
            return this;
        }

        public Builder flushExecutor(ExecutorService flushExecutor) {
            this.flushExecutor = flushExecutor;
            this.ownsFlushExecutor = false;
            return this;
        }

        public Builder ownedFlushExecutor(ExecutorService flushExecutor) {
            this.flushExecutor = flushExecutor;
            this.ownsFlushExecutor = true;
            return this;
        }

        public Builder maintenanceExecutor(ScheduledExecutorService maintenanceExecutor) {
            this.maintenanceExecutor = maintenanceExecutor;
            this.ownsMaintenanceExecutor = false;
            return this;
        }

        public Builder ownedMaintenanceExecutor(ScheduledExecutorService maintenanceExecutor) {
            this.maintenanceExecutor = maintenanceExecutor;
            this.ownsMaintenanceExecutor = true;
            return this;
        }

        public Builder redactor(SensitiveDataRedactor redactor) {
            this.redactor = redactor;
            return this;
        }

        public DatabaseExecutionManager build() {
            return new DatabaseExecutionManager(this);
        }
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        flushCoordinator.ensureOpen();
        repository.save(AssemblyRunRecord.from(execution, redactor));
        buffers.createFresh(execution.getId());
    }

    @Override
    public void append(StationLogRecord stationLogRecord) {
        if (stationLogRecord == null) {
            return;
        }
        flushCoordinator.ensureOpen();
        stationLogRecord = stationLogRecord.redactedWith(redactor);
        UUID runId = stationLogRecord.assemblyLineExecutionId();
        OperationRecordBuffer buffer = buffers.getOrCreate(runId);
        boolean shouldScheduleFlush = buffer.append(stationLogRecord, configuration.batchSize(),
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
    public void flush(UUID runId) {
        if (runId == null) {
            return;
        }
        OperationRecordBuffer buffer = buffers.get(runId);
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
