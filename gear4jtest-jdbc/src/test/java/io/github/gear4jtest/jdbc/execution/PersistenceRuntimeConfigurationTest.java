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
        assertThat(configuration.maxActiveRuns()).isEqualTo(1_000);
        assertThat(configuration.maxBufferedStationLogs()).isEqualTo(10_000);
        assertThat(configuration.flushInterval()).isPositive();
        assertThat(configuration.maxScheduledFlushTasks()).isPositive();
        assertThat(configuration.shutdownRetryInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(configuration.shutdownRetryMaxBackoff()).isEqualTo(Duration.ofSeconds(2));
        assertThat(configuration.jdbcStatementTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.readinessMaxBufferedStationLogs()).isEqualTo(5_000);
        assertThat(configuration.readinessMaxBacklogAge()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.connectivityProbeTimeout()).isEqualTo(Duration.ofSeconds(2));
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
    void toBuilder_shouldPreserveEveryConfigurationProperty() {
        // Given
        PersistenceRuntimeConfiguration original = PersistenceRuntimeConfiguration.builder()
                .batchSize(17)
                .maxPendingLogsPerRun(101)
                .maxActiveRuns(103)
                .maxBufferedStationLogs(107)
                .flushInterval(Duration.ofMillis(250))
                .shutdownTimeout(Duration.ofSeconds(11))
                .shutdownRetryInitialBackoff(Duration.ofMillis(25))
                .shutdownRetryMaxBackoff(Duration.ofMillis(400))
                .flushThreadCount(3)
                .maxScheduledFlushTasks(71)
                .jdbcStatementTimeout(Duration.ofSeconds(7))
                .readinessMaxBufferedStationLogs(83)
                .readinessMaxBacklogAge(Duration.ofSeconds(13))
                .connectivityProbeTimeout(Duration.ofSeconds(5))
                .build();

        // When
        PersistenceRuntimeConfiguration copy = original.toBuilder().batchSize(19).build();

        // Then
        assertThat(original.batchSize()).isEqualTo(17);
        assertThat(copy.batchSize()).isEqualTo(19);
        assertThat(copy.maxPendingLogsPerRun()).isEqualTo(101);
        assertThat(copy.maxActiveRuns()).isEqualTo(103);
        assertThat(copy.maxBufferedStationLogs()).isEqualTo(107);
        assertThat(copy.flushInterval()).isEqualTo(Duration.ofMillis(250));
        assertThat(copy.shutdownTimeout()).isEqualTo(Duration.ofSeconds(11));
        assertThat(copy.shutdownRetryInitialBackoff()).isEqualTo(Duration.ofMillis(25));
        assertThat(copy.shutdownRetryMaxBackoff()).isEqualTo(Duration.ofMillis(400));
        assertThat(copy.flushThreadCount()).isEqualTo(3);
        assertThat(copy.maxScheduledFlushTasks()).isEqualTo(71);
        assertThat(copy.jdbcStatementTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(copy.readinessMaxBufferedStationLogs()).isEqualTo(83);
        assertThat(copy.readinessMaxBacklogAge()).isEqualTo(Duration.ofSeconds(13));
        assertThat(copy.connectivityProbeTimeout()).isEqualTo(Duration.ofSeconds(5));
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

    @Test
    void build_shouldRejectNonPositiveGlobalCapacities() {
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder().maxActiveRuns(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxActiveRuns");
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder().maxBufferedStationLogs(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBufferedStationLogs");
    }

    @Test
    void build_shouldRejectInvalidShutdownRetryBackoff() {
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .shutdownRetryInitialBackoff(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shutdownRetryInitialBackoff");

        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .shutdownRetryInitialBackoff(Duration.ofSeconds(2))
                .shutdownRetryMaxBackoff(Duration.ofMillis(100))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shutdownRetryInitialBackoff must be <= shutdownRetryMaxBackoff");
    }

    @Test
    void build_shouldRejectInvalidReadinessThresholds() {
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .readinessMaxBufferedStationLogs(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readinessMaxBufferedStationLogs");

        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .readinessMaxBacklogAge(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readinessMaxBacklogAge");

        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder()
                .connectivityProbeTimeout(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectivityProbeTimeout");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
