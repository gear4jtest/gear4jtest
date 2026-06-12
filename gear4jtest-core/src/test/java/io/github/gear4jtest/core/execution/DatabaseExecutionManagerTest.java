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
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    void failedAsyncFlush_shouldRestoreDrainedRecordsForLaterRetry() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch failedOnce = new CountDownLatch(1);
        CountDownLatch succeededOnce = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                failedOnce.countDown();
                throw new ExecutionPersistenceException("temporary database outage");
            }
            succeededOnce.countDown();
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(2)
                .maxPendingLogsPerRun(10).flushInterval(Duration.ofDays(1)).build();
        DatabaseExecutionManager manager = new DatabaseExecutionManager(repository, configuration, false,
                flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            // When
            manager.append(record(runId));
            manager.append(record(runId));

            // Then
            assertThat(failedOnce.await(2, TimeUnit.SECONDS)).as("first asynchronous flush should fail").isTrue();
            awaitStats(manager, 1L, 2);

            // When
            manager.flush(runId);

            // Then
            assertThat(succeededOnce.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.snapshotStats().bufferedStationLogs()).isZero();
            verify(repository, atLeast(2)).saveOperationRecordsBatch(anyList());
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void end_shouldKeepRunBufferWhenFinalUpdateFailsSoCallerCanRetry() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new ExecutionPersistenceException("database update failed"))
                .doNothing()
                .when(repository).update(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = new DatabaseExecutionManager(repository,
                PersistenceRuntimeConfiguration.defaults(), false, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();
        AssemblyRunTrace trace = new AssemblyRunTrace(runId, "pipeline", Map.of());
        trace.markSuccess("ok");

        try {
            manager.append(record(runId));

            // When / Then
            assertThatThrownBy(() -> manager.end(trace))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessageContaining("database update failed");
            assertThat(manager.snapshotStats().activeRuns()).as("failed final update must keep retry state")
                    .isEqualTo(1);

            // When
            manager.end(trace);

            // Then
            assertThat(manager.snapshotStats().activeRuns()).isZero();
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

    private static void awaitStats(DatabaseExecutionManager manager,
                                   long expectedFailedFlushes,
                                   int expectedBufferedLogs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PersistenceRuntimeStats stats;
        do {
            stats = manager.snapshotStats();
            if (stats.failedFlushes() == expectedFailedFlushes
                    && stats.bufferedStationLogs() == expectedBufferedLogs) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadline);

        assertThat(stats.failedFlushes()).isEqualTo(expectedFailedFlushes);
        assertThat(stats.bufferedStationLogs()).as("failed flush must not lose the drained records")
                .isEqualTo(expectedBufferedLogs);
    }

    private static StationLogRecord record(UUID runId) {
        return new StationLogRecord(UUID.randomUUID(), runId, "station", null, StationLogStatus.SUCCEEDED,
                Instant.now(), Instant.now(), null, null, Map.of(), null);
    }
}
