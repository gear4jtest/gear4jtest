package io.github.gear4jtest.jdbc.execution;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.CapturingJsonCodec;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.MutablePayload;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.MutablePayloadCloner;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.assertMetadataOnly;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.awaitCompletedFlushes;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.configurationOf;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.manager;
import static io.github.gear4jtest.jdbc.execution.DatabaseExecutionManagerTestFixture.stationRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseExecutionManagerTest {
    @Test
    void bufferedRecord_shouldNotChangeWhenCallerMutatesNestedPayloadBeforeFlush() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(10)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .build();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(configuration)
                .payloadCloner(new MutablePayloadCloner())
                .redactor(SensitiveDataRedactor.none())
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
        UUID runId = UUID.randomUUID();
        MutablePayload callerPayload = new MutablePayload("captured");
        StationLogRecord record = new StationLogRecord(UUID.randomUUID(), runId, "station", null,
                StationLogStatus.SUCCEEDED, Instant.now(), Instant.now(), null, null,
                Map.of("payload", callerPayload), null);

        try {
            // When
            manager.append(record);
            callerPayload.values().add("mutated-before-flush");
            manager.flush(runId);

            // Then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<StationLogRecord>> records = ArgumentCaptor.forClass(List.class);
            verify(repository).saveOperationRecordsBatch(records.capture());
            MutablePayload persisted = (MutablePayload) records.getValue().get(0).context().get("payload");
            assertThat(persisted.values()).containsExactly("captured");
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void defaultRedactionPolicy_shouldPersistMetadataWithoutSensitiveValues() {
        // Given
        String secret = "fixture-secret-must-not-leak";
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "line", Map.of("secret", secret));
        trace.setContext(Map.of("secret", secret));
        trace.setResult(secret);
        trace.setErrorMessage(secret);

        try {
            // When
            manager.start(trace);
            manager.end(trace);

            // Then
            ArgumentCaptor<AssemblyRunRecord> started = ArgumentCaptor.forClass(AssemblyRunRecord.class);
            ArgumentCaptor<AssemblyRunRecord> completed = ArgumentCaptor.forClass(AssemblyRunRecord.class);
            verify(repository).save(started.capture());
            verify(repository).update(completed.capture());
            assertMetadataOnly(started.getValue(), secret);
            assertMetadataOnly(completed.getValue(), secret);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void builder_shouldNotInitializeSchemaUnlessExplicitlyEnabled() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();

        try {
            // Then
            verify(repository, never()).initialize();
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void builder_shouldInitializeSchemaWhenExplicitlyEnabled() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .autoCreateTables(true)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();

        try {
            // Then
            verify(repository).initialize();
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void flushThreshold_shouldPreserveConfigurationRegardlessOfBuilderCallOrder() throws Exception {
        // Given
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(11)
                .maxPendingLogsPerRun(101)
                .flushInterval(Duration.ofHours(1))
                .shutdownTimeout(Duration.ofSeconds(12))
                .shutdownRetryInitialBackoff(Duration.ofMillis(25))
                .shutdownRetryMaxBackoff(Duration.ofMillis(400))
                .flushThreadCount(2)
                .maxScheduledFlushTasks(71)
                .jdbcStatementTimeout(Duration.ofSeconds(7))
                .readinessMaxBufferedStationLogs(83)
                .readinessMaxBacklogAge(Duration.ofSeconds(13))
                .connectivityProbeTimeout(Duration.ofSeconds(5))
                .build();
        ExecutorService flushExecutor = Executors.newFixedThreadPool(2);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager configurationFirst = DatabaseExecutionManager.builder()
                .repository(mock(DatabaseAssemblyRunRepository.class))
                .configuration(configuration)
                .flushThreshold(123)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
        DatabaseExecutionManager thresholdFirst = DatabaseExecutionManager.builder()
                .repository(mock(DatabaseAssemblyRunRepository.class))
                .flushThreshold(123)
                .configuration(configuration)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();

        try {
            // When
            PersistenceRuntimeConfiguration first = configurationOf(configurationFirst);
            PersistenceRuntimeConfiguration second = configurationOf(thresholdFirst);

            // Then
            assertThat(first).usingRecursiveComparison().isEqualTo(second);
            assertThat(first.batchSize()).isEqualTo(123);
            assertThat(first.maxPendingLogsPerRun()).isEqualTo(123);
            assertThat(first.flushInterval()).isEqualTo(Duration.ofHours(1));
            assertThat(first.jdbcStatementTimeout()).isEqualTo(Duration.ofSeconds(7));
            assertThat(first.connectivityProbeTimeout()).isEqualTo(Duration.ofSeconds(5));
        } finally {
            configurationFirst.shutdown(Duration.ofSeconds(1));
            thresholdFirst.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void runFlushThreshold_shouldOverrideManagerBatchSizeForOnlyThatRun() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(10)
                .maxPendingLogsPerRun(20)
                .flushInterval(Duration.ofDays(1))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        AssemblyRunTrace fastRun = new AssemblyRunTrace(UUID.randomUUID(), "fast", Map.of());
        AssemblyRunTrace defaultRun = new AssemblyRunTrace(UUID.randomUUID(), "default", Map.of());

        try {
            manager.start(fastRun, PersistenceConfiguration.builder().stationLogFlushThreshold(2).build());
            manager.start(defaultRun);

            // When
            manager.append(stationRecord(fastRun.getId()));
            manager.append(stationRecord(defaultRun.getId()));
            manager.append(stationRecord(fastRun.getId()));
            manager.append(stationRecord(defaultRun.getId()));
            awaitCompletedFlushes(manager, 1);

            // Then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<StationLogRecord>> batch = ArgumentCaptor.forClass(List.class);
            verify(repository).saveOperationRecordsBatch(batch.capture());
            assertThat(batch.getValue()).hasSize(2)
                    .allMatch(record -> record.assemblyLineExecutionId().equals(fastRun.getId()));
            assertThat(manager.snapshotStats().bufferedStationLogs()).isEqualTo(2);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void start_shouldRejectRunFlushThresholdAboveBoundedBufferBeforeRepositoryWrite() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(2)
                .maxPendingLogsPerRun(3)
                .flushInterval(Duration.ofDays(1))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "run", Map.of());
        PersistenceConfiguration runConfiguration = PersistenceConfiguration.builder()
                .stationLogFlushThreshold(4)
                .build();

        try {
            // When / Then
            assertThatThrownBy(() -> manager.start(run, runConfiguration))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("stationLogFlushThreshold must be <= maxPendingLogsPerRun (3)");
            verify(repository, never()).save(any(AssemblyRunRecord.class));
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void customJsonCodec_shouldReceiveValuesAfterCustomRedaction() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        CapturingJsonCodec jsonCodec = new CapturingJsonCodec();
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .jsonCodec(jsonCodec)
                .configuration(PersistenceRuntimeConfiguration.builder()
                        .flushInterval(Duration.ofHours(1))
                        .build())
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .redactor((target, value) -> switch (target) {
                    case RUN_INPUT -> "masked-input";
                    case RUN_CONTEXT -> Map.of("tenant", "masked");
                    case RUN_RESULT -> "masked-result";
                    default -> value;
                })
                .build();
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "line",
                Map.of("payload", "secret-input"));
        trace.setContext(Map.of("token", "secret-context"));
        trace.setResult("secret-result");

        try {
            // When
            manager.start(trace);

            // Then
            assertThat(jsonCodec.serializedValues())
                    .containsExactly("masked-input", Map.of("tenant", "masked"), "masked-result");
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void append_shouldRejectRecordsWhenPerRunBufferIsFull() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch releaseFlushExecutor = new CountDownLatch(1);
        flushExecutor.submit(() -> {
            try {
                releaseFlushExecutor.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(2)
                .maxPendingLogsPerRun(2).flushInterval(Duration.ofDays(1)).build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            // When
            manager.append(stationRecord(runId));
            manager.append(stationRecord(runId));

            StationLogRecord rejectedRecord = stationRecord(runId);

            // Then
            assertThatThrownBy(() -> manager.append(rejectedRecord))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessageContaining("buffer is full");
            assertThat(manager.snapshotStats().rejectedAppends()).isEqualTo(1L);
        } finally {
            releaseFlushExecutor.countDown();
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void periodicFlush_shouldPersistRecordsBelowBatchThreshold() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch persisted = new CountDownLatch(1);
        doAnswer(invocation -> {
            persisted.countDown();
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(10).flushInterval(Duration.ofMillis(10)).build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);

        try {
            // When
            manager.append(stationRecord(UUID.randomUUID()));

            // Then
            assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
            awaitCompletedFlushes(manager, 1L);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void start_shouldAllowIndependentJdbcWritesToProgressConcurrently() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch bothWritesStarted = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maxConcurrentWrites = new AtomicInteger();
        doAnswer(invocation -> {
            int concurrentWrites = activeWrites.incrementAndGet();
            maxConcurrentWrites.accumulateAndGet(concurrentWrites, Math::max);
            bothWritesStarted.countDown();
            try {
                releaseWrites.await();
            } finally {
                activeWrites.decrementAndGet();
            }
            return null;
        }).when(repository).save(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService operationExecutor = Executors.newFixedThreadPool(2);
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        AssemblyRunTrace firstTrace = new AssemblyRunTrace(UUID.randomUUID(), "first", Map.of());
        AssemblyRunTrace secondTrace = new AssemblyRunTrace(UUID.randomUUID(), "second", Map.of());

        try {
            // When
            Future<?> firstStart = operationExecutor.submit(() -> manager.start(firstTrace));
            Future<?> secondStart = operationExecutor.submit(() -> manager.start(secondTrace));

            // Then
            assertThat(bothWritesStarted.await(2, TimeUnit.SECONDS))
                    .as("independent JDBC writes must not be serialized by a manager-wide lifecycle lock")
                    .isTrue();
            releaseWrites.countDown();
            firstStart.get(2, TimeUnit.SECONDS);
            secondStart.get(2, TimeUnit.SECONDS);
            assertThat(maxConcurrentWrites).hasValue(2);
        } finally {
            releaseWrites.countDown();
            manager.shutdown(Duration.ofSeconds(1));
            operationExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

}
