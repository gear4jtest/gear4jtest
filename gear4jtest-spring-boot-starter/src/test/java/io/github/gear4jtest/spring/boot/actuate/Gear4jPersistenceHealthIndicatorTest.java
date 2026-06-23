package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gear4jPersistenceHealthIndicatorTest {
    @Test
    void health_shouldExposePersistenceRuntimeStatsWhenPersistenceIsHealthy() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.snapshotStats()).thenReturn(new PersistenceRuntimeStats(2, 3, 4, 5, 0, 0));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus())
                .as("persistence manager should be healthy when no persistence failures are known")
                .isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeRuns", 2)
                .containsEntry("bufferedStationLogs", 3)
                .containsEntry("scheduledFlushes", 4L)
                .containsEntry("completedFlushes", 5L)
                .containsEntry("failedFlushes", 0L)
                .containsEntry("rejectedAppends", 0L);
    }

    @Test
    void health_shouldBeDownWhenFlushesFailedOrAppendsWereRejected() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.snapshotStats()).thenReturn(new PersistenceRuntimeStats(2, 3, 4, 5, 6, 7));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus()).as("failed flushes or rejected appends mean persistence is degraded")
                .isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("activeRuns", 2)
                .containsEntry("bufferedStationLogs", 3)
                .containsEntry("failedFlushes", 6L)
                .containsEntry("rejectedAppends", 7L);
    }

    @Test
    void health_shouldBeDownWhenStatsCannotBeRead() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.snapshotStats()).thenThrow(new IllegalStateException("boom"));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When / Then
        assertThat(indicator.health().getStatus()).as("unexpected stats failures should surface in health")
                .isEqualTo(Status.DOWN);
    }
}
