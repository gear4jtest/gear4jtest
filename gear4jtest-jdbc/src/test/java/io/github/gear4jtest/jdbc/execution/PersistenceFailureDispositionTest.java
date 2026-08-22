package io.github.gear4jtest.jdbc.execution;

import java.sql.SQLDataException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PersistenceFailureDispositionTest {
    @Test
    void recordDataFailure_shouldBisectCommitHealthyRecordsAndQuarantineOnlyThePoisonRecord() {
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        List<StationLogRecord> rejected = new ArrayList<>();
        PersistenceBatchProcessor processor = new PersistenceBatchProcessor(counters,
                (record, context) -> rejected.add(record));
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 5, 5);
        List<StationLogRecord> records = List.of(record("first"), record("second"), record("poison"),
                                                 record("fourth"), record("fifth"));
        buffer.appendAll(records, counters);
        List<String> persisted = new ArrayList<>();

        processor.persist(buffer, buffer.drainBatch(), batch -> {
            if (batch.stream().anyMatch(record -> "poison".equals(record.operationId()))) {
                throw new ExecutionPersistenceException("invalid record",
                        new SQLDataException("too long", "22001"));
            }
            batch.stream().map(StationLogRecord::operationId).forEach(persisted::add);
        });

        assertThat(persisted).containsExactly("first", "second", "fourth", "fifth");
        assertThat(rejected).extracting(StationLogRecord::operationId).containsExactly("poison");
        assertThat(buffer.retainedCount()).isZero();
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(5, 5)).quarantinedStationLogs())
                .isEqualTo(1L);
    }

    @Test
    void chainedRecordDataFailure_shouldBisectAndExposeTheDecisiveSqlException() {
        // Given
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        List<StationLogRecord> rejected = new ArrayList<>();
        AtomicReference<RejectedPersistenceRecordContext> rejectionContext = new AtomicReference<>();
        PersistenceBatchProcessor processor = new PersistenceBatchProcessor(counters, (record, context) -> {
            rejected.add(record);
            rejectionContext.set(context);
        });
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 3, 3);
        List<StationLogRecord> records = List.of(record("first"), record("poison"), record("third"));
        buffer.appendAll(records, counters);
        List<String> persisted = new ArrayList<>();

        // When
        processor.persist(buffer, buffer.drainBatch(), batch -> {
            if (batch.stream().anyMatch(record -> "poison".equals(record.operationId()))) {
                SQLException batchFailure = new SQLException("batch failed");
                batchFailure.setNextException(new SQLException("too long", "22001", 14_001));
                throw new ExecutionPersistenceException("invalid record", batchFailure);
            }
            batch.stream().map(StationLogRecord::operationId).forEach(persisted::add);
        });

        // Then
        assertThat(persisted).containsExactly("first", "third");
        assertThat(rejected).extracting(StationLogRecord::operationId).containsExactly("poison");
        assertThat(rejectionContext.get())
                .extracting(RejectedPersistenceRecordContext::failureType,
                            RejectedPersistenceRecordContext::sqlState,
                            RejectedPersistenceRecordContext::vendorCode)
                .containsExactly(SQLException.class.getName(), "22001", 14_001);
        assertThat(buffer.retainedCount()).isZero();
    }

    @Test
    void rejectedRecordHandlerFailure_shouldRestoreTheRecordWithoutCountingItAsQuarantined() {
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        PersistenceBatchProcessor processor = new PersistenceBatchProcessor(counters, (record, context) -> {
            throw new IllegalStateException("rejection sink unavailable");
        });
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);
        buffer.append(record("poison"), counters);

        assertThatThrownBy(() -> processor.persist(buffer, buffer.drainBatch(), batch -> {
            throw new ExecutionPersistenceException("invalid record",
                    new SQLDataException("too long", "22001"));
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("rejection sink unavailable");

        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThat(buffer.retainedCount()).isEqualTo(1);
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(1, 1)).quarantinedStationLogs())
                .isZero();
    }

    @Test
    void unknownConstraintFailure_shouldRemainSystemicWithoutBisectionOrQuarantine() {
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        List<StationLogRecord> rejected = new ArrayList<>();
        PersistenceBatchProcessor processor = new PersistenceBatchProcessor(counters,
                (record, context) -> rejected.add(record));
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2, 2);
        buffer.appendAll(List.of(record("first"), record("second")), counters);
        int[] attempts = { 0 };

        assertThatThrownBy(() -> processor.persist(buffer, buffer.drainBatch(), batch -> {
            attempts[0]++;
            throw new ExecutionPersistenceException("foreign key",
                    new SQLException("foreign key", "23503"));
        })).isInstanceOf(ExecutionPersistenceException.class);

        assertThat(attempts[0]).isEqualTo(1);
        assertThat(rejected).isEmpty();
        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.retainedCount()).isEqualTo(2);
    }

    @Test
    void failedFinalization_shouldBeRetriedByPeriodicMaintenanceWithoutAnotherEndCall() {
        AtomicReference<Runnable> periodicFlush = new AtomicReference<>();
        ScheduledExecutorService maintenanceExecutor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            periodicFlush.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(maintenanceExecutor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));
        ExecutorService flushExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(flushExecutor).execute(any(Runnable.class));
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new ExecutionPersistenceException("temporarily unavailable"))
                .doNothing()
                .when(repository).update(any());
        OperationRecordBufferRegistry buffers = new OperationRecordBufferRegistry(10, 2);
        PersistenceFlushCoordinator coordinator = new PersistenceFlushCoordinator(repository,
                PersistenceRuntimeConfiguration.builder().batchSize(2).maxPendingLogsPerRun(10)
                        .flushInterval(Duration.ofMillis(10)).build(),
                buffers, flushExecutor, maintenanceExecutor, false, false);
        OperationRecordBuffer buffer = buffers.createFresh(UUID.randomUUID(), 2);
        buffer.beginFinalization(finalRecord(buffer.runId()));

        assertThatThrownBy(() -> coordinator.finalizeBufferBlocking(buffer))
                .isInstanceOf(ExecutionPersistenceException.class);
        assertThat(buffers.activeRunCount()).isEqualTo(1);

        periodicFlush.get().run();

        assertThat(buffers.activeRunCount()).isZero();
        verify(repository, times(2)).update(any());
    }

    private static StationLogRecord record(String operationId) {
        java.time.Instant now = java.time.Instant.now();
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), operationId, null, null,
                StationLogStatus.SUCCEEDED, now, now, null, null, Map.of(), null);
    }

    private static AssemblyRunRecord finalRecord(UUID runId) {
        java.time.Instant now = java.time.Instant.now();
        return new AssemblyRunRecord(runId, "line", Map.of(), null, null, ExecutionStatus.SUCCEEDED,
                now, now, null, null, runId, null);
    }
}
