package io.github.gear4jtest.core.execution;

import java.time.Duration;

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
    }

    @Test
    void build_shouldRejectBufferSmallerThanBatch() {
        // When / Then
        assertThatThrownBy(() -> PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(9).flushInterval(Duration.ofSeconds(1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPendingLogsPerRun");
    }
}
