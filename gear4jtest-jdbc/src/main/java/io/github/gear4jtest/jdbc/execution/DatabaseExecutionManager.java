package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.PersistenceFlushObserver;
import io.github.gear4jtest.core.persistence.PersistenceFlushSubscription;
import io.github.gear4jtest.core.persistence.PersistenceOperationalStatus;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import io.github.gear4jtest.jdbc.persistence.PersistenceJsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed run manager with bounded station-log buffering and asynchronous
 * batched flushes. Independent run operations may invoke the repository
 * concurrently; supplied repositories and data sources must therefore be
 * thread-safe.
 */
public class DatabaseExecutionManager implements RunPersistenceManager, PersistenceRuntimeMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutionManager.class);
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final OperationRecordBufferRegistry buffers;
    private final PersistenceFlushCoordinator flushCoordinator;
    private final PersistenceConnectivityProbe connectivityProbe;
    private final SensitiveDataRedactor redactor;
    private final PayloadCloner payloadCloner;

    public static Builder builder() {
        return new Builder();
    }

    private DatabaseExecutionManager(Builder builder) {
        this.configuration = builder.effectiveConfiguration();
        this.repository = builder.repository != null
                ? builder.repository
                : createRepository(builder.dataSource, builder.databaseDialect, this.configuration,
                                   builder.baselineOnMigrate, builder.jsonCodec, builder.transactionOperations);
        this.buffers = new OperationRecordBufferRegistry(configuration.maxPendingLogsPerRun(),
                configuration.batchSize());
        this.redactor = builder.redactor != null ? builder.redactor : SensitiveDataRedactor.discardSensitiveValues();
        this.payloadCloner = builder.payloadCloner != null ? builder.payloadCloner : PayloadCloners.immutableAware();
        this.connectivityProbe = new PersistenceConnectivityProbe();
        if (SensitiveDataRedactor.isNone(this.redactor)) {
            LOGGER.warn("[Gear4J] JDBC persistence is configured to allow unredacted sensitive data capture. "
                    + "Assembly line payloads, contexts, results and error messages will be persisted as-is.");
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
                                                                  PersistenceRuntimeConfiguration configuration,
                                                                  boolean baselineOnMigrate,
                                                                  PersistenceJsonCodec jsonCodec,
                                                                  JdbcTransactionOperations transactionOperations) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        DatabaseAssemblyRunRepository.Builder repositoryBuilder = DatabaseAssemblyRunRepository.builder()
                .dataSource(Objects.requireNonNull(dataSource, "dataSource must not be null"))
                .databaseDialect(Objects.requireNonNull(databaseDialect, "databaseDialect must not be null"))
                .jsonCodec(Objects.requireNonNull(jsonCodec, "jsonCodec must not be null"))
                .jdbcStatementTimeout(configuration.jdbcStatementTimeout())
                .baselineOnMigrate(baselineOnMigrate);
        if (transactionOperations != null) {
            repositoryBuilder.transactionOperations(transactionOperations);
        }
        return repositoryBuilder.build();
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private DatabaseAssemblyRunRepository repository;
        private PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.defaults();
        private Integer flushThreshold;
        private PersistenceJsonCodec jsonCodec = PersistenceJsonCodec.jackson(new ObjectMapper());
        private boolean autoCreateTables;
        private boolean baselineOnMigrate;
        private ExecutorService flushExecutor;
        private ScheduledExecutorService maintenanceExecutor;
        private boolean ownsFlushExecutor;
        private boolean ownsMaintenanceExecutor;
        private SensitiveDataRedactor redactor;
        private PayloadCloner payloadCloner;
        private JdbcTransactionOperations transactionOperations;

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
            this.flushThreshold = flushThreshold;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.jsonCodec = PersistenceJsonCodec.jackson(objectMapper);
            return this;
        }

        public Builder jsonCodec(PersistenceJsonCodec jsonCodec) {
            this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
            return this;
        }

        public Builder autoCreateTables(boolean autoCreateTables) {
            this.autoCreateTables = autoCreateTables;
            return this;
        }

        /**
         * Explicitly allows auto-creation to baseline a compatible existing schema
         * without Gear4J migration history. Disabled by default.
         */
        public Builder baselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
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

        public Builder payloadCloner(PayloadCloner payloadCloner) {
            this.payloadCloner = Objects.requireNonNull(payloadCloner, "payloadCloner must not be null");
            return this;
        }

        /**
         * Configures ownership of JDBC write transactions for the repository created by
         * this builder.
         */
        public Builder transactionOperations(JdbcTransactionOperations transactionOperations) {
            this.transactionOperations = Objects.requireNonNull(transactionOperations,
                                                                "transactionOperations must not be null");
            return this;
        }

        public DatabaseExecutionManager build() {
            return new DatabaseExecutionManager(this);
        }

        private PersistenceRuntimeConfiguration effectiveConfiguration() {
            PersistenceRuntimeConfiguration configured = Objects.requireNonNull(configuration,
                                                                                "configuration must not be null");
            if (flushThreshold == null) {
                return configured;
            }
            return configured.toBuilder()
                    .batchSize(flushThreshold)
                    .maxPendingLogsPerRun(Math.max(configured.maxPendingLogsPerRun(), flushThreshold))
                    .build();
        }
    }

    @Override
    public void start(RunTrace execution) {
        start(execution, null);
    }

    @Override
    public void start(RunTrace execution, PersistenceConfiguration runConfiguration) {
        Objects.requireNonNull(execution, "execution must not be null");
        int flushThreshold = effectiveFlushThreshold(runConfiguration);
        flushCoordinator.executeWhileOpen(execution.getId(), () -> {
            repository.save(AssemblyRunRecord.from(execution, redactor, payloadCloner));
            buffers.createFresh(execution.getId(), flushThreshold);
        });
    }

    private int effectiveFlushThreshold(PersistenceConfiguration runConfiguration) {
        int flushThreshold = runConfiguration == null
                ? configuration.batchSize()
                : runConfiguration.getStationLogFlushThreshold().orElse(configuration.batchSize());
        if (flushThreshold > configuration.maxPendingLogsPerRun()) {
            throw new IllegalArgumentException("stationLogFlushThreshold must be <= maxPendingLogsPerRun ("
                    + configuration.maxPendingLogsPerRun() + ")");
        }
        return flushThreshold;
    }

    @Override
    public void append(StationLogRecord stationLogRecord) {
        if (stationLogRecord == null) {
            return;
        }
        flushCoordinator.ensureOpen();
        StationLogRecord redactedRecord = stationLogRecord.redactedWith(redactor, payloadCloner);
        flushCoordinator.executeWhileOpen(redactedRecord.assemblyLineExecutionId(),
                                          () -> appendRunBatch(List.of(redactedRecord)));
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        flushCoordinator.ensureOpen();
        Map<UUID, List<StationLogRecord>> recordsByRun = new LinkedHashMap<>();
        for (StationLogRecord record : records) {
            if (record == null) {
                continue;
            }
            StationLogRecord redactedRecord = record.redactedWith(redactor, payloadCloner);
            recordsByRun.computeIfAbsent(redactedRecord.assemblyLineExecutionId(), ignored -> new ArrayList<>())
                    .add(redactedRecord);
        }
        if (recordsByRun.isEmpty()) {
            return;
        }
        flushCoordinator.executeWhileOpen(List.copyOf(recordsByRun.keySet()),
                                          () -> recordsByRun.values().forEach(this::appendRunBatch));
    }

    private void appendRunBatch(List<StationLogRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        UUID runId = records.get(0).assemblyLineExecutionId();
        OperationRecordBuffer buffer = buffers.getOrCreate(runId);
        boolean shouldScheduleFlush = buffer.appendAll(records, flushCoordinator.counters());
        if (shouldScheduleFlush) {
            flushCoordinator.scheduleAsyncFlush(buffer, false);
        }
    }

    @Override
    public void flush(UUID runId) {
        if (runId == null) {
            return;
        }
        flushCoordinator.executeWhileOpen(runId, () -> flushWhileOpen(runId));
    }

    private void flushWhileOpen(UUID runId) {
        OperationRecordBuffer buffer = buffers.get(runId);
        if (buffer != null) {
            buffer.assertHealthy();
            flushCoordinator.flushBufferBlocking(buffer, false);
            buffer.assertHealthy();
        }
    }

    @Override
    public void end(RunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        flushCoordinator.executeWhileOpen(finalExecution.getId(), () -> endWhileOpen(finalExecution));
    }

    private void endWhileOpen(RunTrace finalExecution) {
        UUID runId = finalExecution.getId();
        OperationRecordBuffer buffer = buffers.getOrCreate(runId);
        buffer.close();
        boolean completed = false;
        try {
            buffer.assertHealthy();
            flushCoordinator.flushBufferBlocking(buffer, true);
            buffer.assertHealthy();
            try {
                repository.update(AssemblyRunRecord.from(finalExecution, redactor, payloadCloner));
                buffer.clearFinalizationFailure();
            } catch (RuntimeException exception) {
                buffer.recordFinalizationFailure(exception);
                throw exception;
            }
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
    public PersistenceFlushSubscription subscribeToFlushes(PersistenceFlushObserver observer) {
        return flushCoordinator.subscribeToFlushes(observer);
    }

    @Override
    public boolean isAlive() {
        return !flushCoordinator.isShutdown();
    }

    @Override
    public PersistenceOperationalStatus probeHealth() {
        PersistenceRuntimeStats stats = snapshotStats();
        if (stats.shutdown()) {
            return operationalStatus(false, false, false, false,
                                     PersistenceOperationalStatus.Reason.SHUT_DOWN, stats);
        }

        boolean connectivityAvailable;
        try {
            connectivityAvailable = connectivityProbe.execute(configuration.connectivityProbeTimeout(),
                                                              () -> repository.checkConnectivity(configuration
                                                                      .connectivityProbeTimeout()));
        } catch (RuntimeException exception) {
            connectivityAvailable = false;
        }
        if (!connectivityAvailable) {
            return operationalStatus(true, false, true, false,
                                     PersistenceOperationalStatus.Reason.CONNECTIVITY_UNAVAILABLE, stats);
        }

        boolean recoveredAfterFailure = isAfter(stats.lastSuccessfulFlushAt(), stats.lastFailedFlushAt());
        boolean failedBacklogPending = stats.bufferedStationLogs() > 0
                && stats.lastFailedFlushAt() != null
                && !recoveredAfterFailure;
        if (failedBacklogPending) {
            return operationalStatus(true, false, true, true,
                                     PersistenceOperationalStatus.Reason.RECOVERY_PENDING, stats);
        }
        if (stats.bufferedStationLogs() > configuration.readinessMaxBufferedStationLogs()) {
            return operationalStatus(true, false, true, true,
                                     PersistenceOperationalStatus.Reason.BACKLOG_SIZE_EXCEEDED, stats);
        }
        if (stats.oldestBufferedStationLogAge().compareTo(configuration.readinessMaxBacklogAge()) > 0) {
            return operationalStatus(true, false, true, true,
                                     PersistenceOperationalStatus.Reason.BACKLOG_AGE_EXCEEDED, stats);
        }
        return operationalStatus(true, true, true, true, PersistenceOperationalStatus.Reason.READY, stats);
    }

    private static PersistenceOperationalStatus operationalStatus(boolean live,
                                                                  boolean ready,
                                                                  boolean connectivityVerified,
                                                                  boolean connectivityAvailable,
                                                                  PersistenceOperationalStatus.Reason reason,
                                                                  PersistenceRuntimeStats stats) {
        return new PersistenceOperationalStatus(live, ready, connectivityVerified, connectivityAvailable,
                isAfter(stats.lastSuccessfulFlushAt(), stats.lastFailedFlushAt()), reason, stats.observedAt(), stats);
    }

    private static boolean isAfter(java.time.Instant candidate, java.time.Instant reference) {
        return candidate != null && reference != null && candidate.isAfter(reference);
    }

    @Override
    public void shutdown() {
        shutdownWithReport(configuration.shutdownTimeout());
    }

    public void shutdown(Duration timeout) {
        shutdownWithReport(timeout);
    }

    /** Shuts down persistence and returns an immutable drain/retry report. */
    public PersistenceShutdownReport shutdownWithReport() {
        return shutdownWithReport(configuration.shutdownTimeout());
    }

    /**
     * Shuts down persistence within the supplied deadline and reports any
     * remainder.
     */
    public PersistenceShutdownReport shutdownWithReport(Duration timeout) {
        connectivityProbe.shutdown();
        return flushCoordinator.shutdown(timeout);
    }

    public Optional<PersistenceShutdownReport> lastShutdownReport() {
        return Optional.ofNullable(flushCoordinator.shutdownReport());
    }
}
