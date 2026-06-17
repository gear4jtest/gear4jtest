package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceRuntimeConfigurationTest {
    @Test
    void defaults_shouldBoundMemoryAndSchedulePeriodicFlush() {
        // When
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.defaults();

        // Then
        assertThat(configuration.batchSize()).isPositive();
        assertThat(configuration.maxPendingLogsPerRun()).isGreaterThanOrEqualTo(configuration.batchSize());
        assertThat(configuration.flushInterval()).isPositive();
        assertThat(configuration.maxScheduledFlushTasks()).isPositive();
    }

    @Test
    void createFlushExecutor_shouldRejectWhenBoundedQueueIsFull() throws Exception {
        // Given
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .flushThreadCount(1)
                .maxScheduledFlushTasks(1)
                .build();
        ExecutorService executor = PersistenceFlushCoordinator.createFlushExecutor(configuration);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(() -> await(releaseWorker));
            executor.execute(() -> {
                // queued task intentionally fills the bounded flush backlog
            });

            // When / Then
            assertThatThrownBy(() -> executor.execute(() -> {
                // rejected task
            }))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void build_shouldRejectBufferSmallerThanBatch() {
        // When / Then
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(9).flushInterval(Duration.ofSeconds(1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPendingLogsPerRun");
    }

    @Test
    void build_shouldRejectNonPositiveFlushBacklogCapacity() {
        // When / Then
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .maxScheduledFlushTasks(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxScheduledFlushTasks");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
