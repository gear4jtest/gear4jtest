package io.github.gear4jtest.micrometer;

import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistenceMetricsBinderTest {
    @Test
    void bind_shouldExposeAllPersistenceRuntimeStats() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.snapshotStats()).thenReturn(new PersistenceRuntimeStats(2, 3, 4, 5, 6, 7));

        // When
        PersistenceMetricsBinder.bind(meterRegistry, manager);

        // Then
        assertThat(meterRegistry.get("gear4j.persistence.active.runs").gauge().value()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("gear4j.persistence.buffered.station.logs").gauge().value()).isEqualTo(3.0d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.scheduled").gauge().value()).isEqualTo(4.0d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.completed").gauge().value()).isEqualTo(5.0d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.failed").gauge().value()).isEqualTo(6.0d);
        assertThat(meterRegistry.get("gear4j.persistence.appends.rejected").gauge().value()).isEqualTo(7.0d);
    }
}
