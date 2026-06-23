package io.github.gear4jtest.jdbc.execution;

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
        assertThat(configuration.jdbcStatementTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void build_shouldAllowDisablingJdbcStatementTimeout() {
        // When
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .jdbcStatementTimeout(Duration.ZERO)
                .build();

        // Then
        assertThat(configuration.jdbcStatementTimeout()).isZero();
    }

    @Test
    void build_shouldRejectNegativeJdbcStatementTimeout() {
        PersistenceRuntimeConfiguration.Builder builder = PersistenceRuntimeConfiguration.builder()
                .jdbcStatementTimeout(Duration.ofMillis(-1));

        // When / Then
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcStatementTimeout");
    }

    @Test
    void createFlushExecutor_shouldRejectWhenBoundedQueueIsFull() {
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

            Runnable rejectedTask = () -> {
                // rejected task
            };

            // When / Then
            assertThatThrownBy(() -> executor.execute(rejectedTask))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void build_shouldRejectBufferSmallerThanBatch() {
        PersistenceRuntimeConfiguration.Builder builder = PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(9).flushInterval(Duration.ofSeconds(1));

        // When / Then
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPendingLogsPerRun");
    }

    @Test
    void build_shouldRejectNonPositiveFlushBacklogCapacity() {
        PersistenceRuntimeConfiguration.Builder builder = PersistenceRuntimeConfiguration.builder()
                .maxScheduledFlushTasks(0);

        // When / Then
        assertThatThrownBy(builder::build)
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
