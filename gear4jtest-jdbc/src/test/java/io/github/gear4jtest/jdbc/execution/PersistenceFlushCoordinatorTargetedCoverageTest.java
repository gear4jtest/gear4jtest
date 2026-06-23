package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    void shutdown_shouldRejectNegativeTimeout() {
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), mock(ExecutorService.class),
                                                              false, false);

        assertThatThrownBy(() -> coordinator.shutdown(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must not be negative");
    }

    @Test
    void scheduleAsyncFlush_shouldFailBufferWhenExecutorRejectsFlush() {
        ExecutorService flushExecutor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("full")).when(flushExecutor).execute(any(Runnable.class));
        PersistenceFlushCoordinator coordinator = coordinator(mock(ScheduledFuture.class), flushExecutor, false, false);
        OperationRecordBuffer buffer = new OperationRecordBuffer(java.util.UUID.randomUUID(), 2);

        assertThatThrownBy(() -> coordinator.scheduleAsyncFlush(buffer, false))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("flush executor rejected");
        assertThat(coordinator.snapshotStats().scheduledFlushes()).isEqualTo(1L);
        assertThat(coordinator.snapshotStats().failedFlushes()).isEqualTo(1L);
        assertThatThrownBy(buffer::assertHealthy).isInstanceOf(ExecutionPersistenceException.class);
    }

    private static PersistenceFlushCoordinator coordinator(ScheduledFuture<?> periodicTask,
                                                           ExecutorService flushExecutor,
                                                           boolean ownsFlushExecutor,
                                                           boolean ownsMaintenanceExecutor) {
        ScheduledExecutorService maintenanceExecutor = mock(ScheduledExecutorService.class);
        doReturn(periodicTask)
                .when(maintenanceExecutor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));
        return new PersistenceFlushCoordinator(mock(DatabaseAssemblyRunRepository.class),
                PersistenceRuntimeConfiguration.builder()
                        .batchSize(2)
                        .maxPendingLogsPerRun(10)
                        .flushInterval(Duration.ofMillis(10))
                        .build(),
                new OperationRecordBufferRegistry(10), flushExecutor, maintenanceExecutor, ownsFlushExecutor,
                ownsMaintenanceExecutor);
    }
}
