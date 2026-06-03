package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class DatabaseExecutionManagerTest {
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
        DatabaseExecutionManager manager = new DatabaseExecutionManager(repository, configuration, false,
                flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            // When
            manager.append(record(runId));
            manager.append(record(runId));

            // Then
            assertThatThrownBy(() -> manager.append(record(runId)))
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
        DatabaseExecutionManager manager = new DatabaseExecutionManager(repository, configuration, false,
                flushExecutor, scheduler);

        try {
            // When
            manager.append(record(UUID.randomUUID()));

            // Then
            assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.snapshotStats().completedFlushes()).isGreaterThanOrEqualTo(1L);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdown_shouldNotShutdownCallerManagedExecutors() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = new DatabaseExecutionManager(repository,
                PersistenceRuntimeConfiguration.defaults(), false, flushExecutor, scheduler);

        try {
            // When
            manager.shutdown(Duration.ofSeconds(1));

            // Then
            assertThat(flushExecutor.isShutdown()).isFalse();
            assertThat(scheduler.isShutdown()).isFalse();
        } finally {
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private static StationLogRecord record(UUID runId) {
        return new StationLogRecord(UUID.randomUUID(), runId, "station", null, StationLogStatus.SUCCEEDED,
                Instant.now(), Instant.now(), null, null, Map.of(), null);
    }
}
