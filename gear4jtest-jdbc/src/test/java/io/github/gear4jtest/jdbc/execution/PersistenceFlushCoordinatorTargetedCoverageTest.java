package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.PersistenceFlushObservation;
import io.github.gear4jtest.core.persistence.PersistenceFlushSubscription;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PersistenceFlushCoordinatorTargetedCoverageTest {
    @Test
    void ensureOpen_shouldFailAfterShutdown() {
        ScheduledFuture<?> periodicTask = mock(ScheduledFuture.class);
        PersistenceFlushCoordinator coordinator = coordinator(periodicTask, mock(ExecutorService.class), false, false);

        coordinator.shutdown(Duration.ofMillis(10));

        assertThatThrownBy(coordinator::ensureOpen)
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessage("DatabaseExecutionManager is already shut down");
        verify(periodicTask).cancel(false);
    }

    @Test
    void closedCoordinator_shouldClearSchedulingAndNotRescheduleRemainingBatch() {
        // Given
        ExecutorService flushExecutor = mock(ExecutorService.class);
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), flushExecutor, false,
                                                              false);
        coordinator.shutdown(Duration.ofMillis(10));
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 10, 2);

        // When
        coordinator.scheduleAsyncFlush(buffer, false);
        assertThat(buffer.markFlushScheduled()).isTrue();
        buffer.appendAll(List.of(mock(StationLogRecord.class), mock(StationLogRecord.class),
                                 mock(StationLogRecord.class), mock(StationLogRecord.class)),
                         coordinator.counters());
        coordinator.flushBufferBlocking(buffer, false);

        // Then
        assertThat(buffer.pendingCount()).isEqualTo(2);
        verifyNoInteractions(flushExecutor);
    }

    @Test
    void shutdown_shouldRejectNegativeTimeout() {
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), mock(ExecutorService.class),
                                                              false, false);

        assertThatThrownBy(() -> coordinator.shutdown(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must not be negative");
    }

    @Test
    void shutdown_shouldReportDroppedTasksAndOwnedExecutorTermination() throws Exception {
        // Given
        ExecutorService flushExecutor = mock(ExecutorService.class);
        Runnable droppedTask = () -> {
            // queued task superseded by the synchronous shutdown drain
        };
        doReturn(List.of(droppedTask)).when(flushExecutor).shutdownNow();
        doReturn(true).when(flushExecutor).awaitTermination(anyLong(), eq(TimeUnit.NANOSECONDS));
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), flushExecutor, true, true);

        // When
        PersistenceShutdownReport report = coordinator.shutdown(Duration.ofSeconds(1));

        // Then
        assertThat(report.successful()).isTrue();
        assertThat(report.droppedFlushTasks()).isEqualTo(1);
        assertThat(report.flushExecutorTerminated()).isTrue();
        verify(flushExecutor).shutdownNow();
    }

    @Test
    void scheduleAsyncFlush_shouldFailBufferWhenExecutorRejectsFlush() {
        ExecutorService flushExecutor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("full")).when(flushExecutor).execute(any(Runnable.class));
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), flushExecutor, false, false);
        OperationRecordBuffer buffer = new OperationRecordBuffer(java.util.UUID.randomUUID(), 2, 2);
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        coordinator.subscribeToFlushes(observations::add);

        assertThatThrownBy(() -> coordinator.scheduleAsyncFlush(buffer, false))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("flush executor rejected");
        assertThat(coordinator.snapshotStats().scheduledFlushes()).isEqualTo(1L);
        assertThat(coordinator.snapshotStats().failedFlushes()).isEqualTo(1L);
        assertThatThrownBy(buffer::assertHealthy).isInstanceOf(ExecutionPersistenceException.class);
        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.trigger()).isEqualTo(PersistenceFlushObservation.Trigger.ASYNC);
            assertThat(observation.outcome()).isEqualTo(PersistenceFlushObservation.Outcome.REJECTED);
            assertThat(observation.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
        });
    }

    @Test
    void scheduleAsyncFlush_shouldIgnoreDuplicateScheduling() {
        // Given
        ExecutorService flushExecutor = mock(ExecutorService.class);
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), flushExecutor, false,
                                                              false);
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2, 2);
        assertThat(buffer.markFlushScheduled()).isTrue();

        // When
        coordinator.scheduleAsyncFlush(buffer, false);

        // Then
        assertThat(coordinator.snapshotStats().scheduledFlushes()).isZero();
        verifyNoInteractions(flushExecutor);
    }

    @Test
    void scheduleAsyncFlush_shouldKeepOpenBufferRetryableAfterTransientFailure() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new IllegalStateException("temporary failure")).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = inlineExecutor();
        PersistenceFlushCoordinator coordinator = coordinator(repository, mock(ScheduledFuture.class), flushExecutor,
                                                              new OperationRecordBufferRegistry(10, 2), false, false);
        OperationRecordBuffer buffer = bufferedRecord(coordinator, false);
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        coordinator.subscribeToFlushes(observations::add);

        // When
        coordinator.scheduleAsyncFlush(buffer, false);

        // Then
        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThat(buffer.currentFailure()).isNull();
        assertThat(coordinator.snapshotStats().failedFlushes()).isEqualTo(1L);
        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.trigger()).isEqualTo(PersistenceFlushObservation.Trigger.ASYNC);
            assertThat(observation.outcome()).isEqualTo(PersistenceFlushObservation.Outcome.FAILED);
        });
    }

    @Test
    void scheduleAsyncFlush_shouldFailClosedBufferAfterTransientFailure() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new IllegalStateException("temporary failure")).when(repository).saveOperationRecordsBatch(anyList());
        PersistenceFlushCoordinator coordinator = coordinator(repository, mock(ScheduledFuture.class),
                                                              inlineExecutor(),
                                                              new OperationRecordBufferRegistry(10, 2),
                                                              false, false);
        OperationRecordBuffer buffer = bufferedRecord(coordinator, true);

        // When
        coordinator.scheduleAsyncFlush(buffer, false);

        // Then
        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThatThrownBy(buffer::assertHealthy).isInstanceOf(ExecutionPersistenceException.class);
    }

    @Test
    void scheduleAsyncFlush_shouldRecordFailureWhenCompleteDrainFails() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new IllegalStateException("terminal failure")).when(repository).saveOperationRecordsBatch(anyList());
        PersistenceFlushCoordinator coordinator = coordinator(repository, mock(ScheduledFuture.class),
                                                              inlineExecutor(),
                                                              new OperationRecordBufferRegistry(10, 2),
                                                              false, false);
        OperationRecordBuffer buffer = bufferedRecord(coordinator, false);

        // When
        coordinator.scheduleAsyncFlush(buffer, true);

        // Then
        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThatThrownBy(buffer::assertHealthy).isInstanceOf(ExecutionPersistenceException.class);
    }

    @Test
    void flushBufferBlocking_shouldRescheduleWhenAnotherBatchRemains() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = mock(ExecutorService.class);
        PersistenceFlushCoordinator coordinator = coordinator(repository, mock(ScheduledFuture.class), flushExecutor,
                                                              new OperationRecordBufferRegistry(10, 2), false, false);
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 10, 2);
        buffer.appendAll(List.of(mock(StationLogRecord.class), mock(StationLogRecord.class),
                                 mock(StationLogRecord.class), mock(StationLogRecord.class)),
                         coordinator.counters());

        // When
        coordinator.flushBufferBlocking(buffer, false);

        // Then
        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.retainedCount()).isEqualTo(2);
        assertThat(coordinator.snapshotStats().scheduledFlushes()).isEqualTo(1L);
        verify(repository).saveOperationRecordsBatch(anyList());
        verify(flushExecutor).execute(any(Runnable.class));
    }

    @Test
    void flushBufferBlocking_shouldObserveAttemptAndIsolateObserverFailures() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        PersistenceFlushCoordinator coordinator = coordinator(repository, mock(ScheduledFuture.class),
                                                              mock(ExecutorService.class),
                                                              new OperationRecordBufferRegistry(10, 2), false, false);
        OperationRecordBuffer buffer = bufferedRecord(coordinator, false);
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        coordinator.subscribeToFlushes(observation -> {
            throw new IllegalStateException("broken metrics backend");
        });
        coordinator.subscribeToFlushes(observations::add);

        // When
        coordinator.flushBufferBlocking(buffer, false);

        // Then
        verify(repository).saveOperationRecordsBatch(anyList());
        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.trigger()).isEqualTo(PersistenceFlushObservation.Trigger.EXPLICIT);
            assertThat(observation.outcome()).isEqualTo(PersistenceFlushObservation.Outcome.SUCCEEDED);
        });
    }

    @Test
    void flushBufferBlocking_shouldNotObserveNoOpFlush() {
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class),
                                                              mock(ExecutorService.class), false, false);
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        coordinator.subscribeToFlushes(observations::add);

        coordinator.flushBufferBlocking(new OperationRecordBuffer(UUID.randomUUID(), 10, 2), false);

        assertThat(observations).isEmpty();
    }

    @Test
    void flushSubscription_shouldBeRemovable() {
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class),
                                                              mock(ExecutorService.class), false, false);
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        PersistenceFlushSubscription subscription = coordinator.subscribeToFlushes(observations::add);
        subscription.close();

        coordinator.flushBufferBlocking(bufferedRecord(coordinator, false), false);

        assertThat(observations).isEmpty();
    }

    @Test
    void shutdown_shouldObserveSuccessfulDrain() {
        // Given
        OperationRecordBufferRegistry buffers = new OperationRecordBufferRegistry(10, 2);
        PersistenceFlushCoordinator coordinator = coordinator(mock(DatabaseAssemblyRunRepository.class),
                                                              mock(ScheduledFuture.class),
                                                              mock(ExecutorService.class), buffers, false, false);
        OperationRecordBuffer buffer = buffers.createFresh(UUID.randomUUID(), 2);
        buffer.append(mock(StationLogRecord.class), coordinator.counters());
        List<PersistenceFlushObservation> observations = new ArrayList<>();
        coordinator.subscribeToFlushes(observations::add);

        // When
        PersistenceShutdownReport report = coordinator.shutdown(Duration.ofSeconds(1));

        // Then
        assertThat(report.successful()).isTrue();
        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.trigger()).isEqualTo(PersistenceFlushObservation.Trigger.SHUTDOWN);
            assertThat(observation.outcome()).isEqualTo(PersistenceFlushObservation.Outcome.SUCCEEDED);
        });
    }

    @Test
    void periodicFlush_shouldScheduleOnlyOpenBuffersContainingRecords() {
        // Given
        AtomicReference<Runnable> periodicFlush = new AtomicReference<>();
        ScheduledFuture<?> periodicTask = mock(ScheduledFuture.class);
        ScheduledExecutorService maintenanceExecutor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            periodicFlush.set(invocation.getArgument(0));
            return periodicTask;
        }).when(maintenanceExecutor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));
        ExecutorService flushExecutor = mock(ExecutorService.class);
        OperationRecordBufferRegistry buffers = new OperationRecordBufferRegistry(10, 2);
        PersistenceFlushCoordinator coordinator = new PersistenceFlushCoordinator(
                mock(DatabaseAssemblyRunRepository.class), configuration(), buffers, flushExecutor,
                maintenanceExecutor, false, false);
        buffers.createFresh(UUID.randomUUID(), 2);
        OperationRecordBuffer closedBuffer = buffers.createFresh(UUID.randomUUID(), 2);
        closedBuffer.append(mock(StationLogRecord.class), coordinator.counters());
        closedBuffer.close();
        OperationRecordBuffer openBuffer = buffers.createFresh(UUID.randomUUID(), 2);
        openBuffer.append(mock(StationLogRecord.class), coordinator.counters());

        // When
        periodicFlush.get().run();

        // Then
        assertThat(coordinator.snapshotStats().scheduledFlushes()).isEqualTo(1L);
        verify(flushExecutor).execute(any(Runnable.class));
    }

    private static ExecutorService inlineExecutor() {
        ExecutorService flushExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(flushExecutor).execute(any(Runnable.class));
        return flushExecutor;
    }

    private static OperationRecordBuffer bufferedRecord(PersistenceFlushCoordinator coordinator, boolean closed) {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 10, 2);
        buffer.append(mock(StationLogRecord.class), coordinator.counters());
        if (closed) {
            buffer.close();
        }
        return buffer;
    }

    private static PersistenceFlushCoordinator coordinator(ScheduledFuture<?> periodicTask,
                                                           ExecutorService flushExecutor,
                                                           boolean ownsFlushExecutor,
                                                           boolean ownsMaintenanceExecutor) {
        return coordinator(mock(DatabaseAssemblyRunRepository.class), periodicTask, flushExecutor,
                           new OperationRecordBufferRegistry(10, 2), ownsFlushExecutor, ownsMaintenanceExecutor);
    }

    private static PersistenceFlushCoordinator coordinator(DatabaseAssemblyRunRepository repository,
                                                           ScheduledFuture<?> periodicTask,
                                                           ExecutorService flushExecutor,
                                                           OperationRecordBufferRegistry buffers,
                                                           boolean ownsFlushExecutor,
                                                           boolean ownsMaintenanceExecutor) {
        ScheduledExecutorService maintenanceExecutor = mock(ScheduledExecutorService.class);
        doReturn(periodicTask)
                .when(maintenanceExecutor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));
        return new PersistenceFlushCoordinator(repository, configuration(), buffers, flushExecutor,
                maintenanceExecutor, ownsFlushExecutor,
                ownsMaintenanceExecutor);
    }

    private static PersistenceRuntimeConfiguration configuration() {
        return PersistenceRuntimeConfiguration.builder()
                .batchSize(2)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofMillis(10))
                .build();
    }
}
