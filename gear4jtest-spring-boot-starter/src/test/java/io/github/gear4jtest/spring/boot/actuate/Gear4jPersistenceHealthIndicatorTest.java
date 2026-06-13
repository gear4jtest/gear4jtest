package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gear4jPersistenceHealthIndicatorTest {
    @Test
    void health_shouldExposePersistenceRuntimeStats() {
        // Given
        DatabaseExecutionManager manager = mock(DatabaseExecutionManager.class);
        when(manager.snapshotStats()).thenReturn(new PersistenceRuntimeStats(2, 3, 4, 5, 6, 7));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus()).as("persistence manager should be considered healthy when stats are readable")
                .isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeRuns", 2)
                .containsEntry("bufferedStationLogs", 3)
                .containsEntry("failedFlushes", 6L)
                .containsEntry("rejectedAppends", 7L);
    }

    @Test
    void health_shouldBeDownWhenStatsCannotBeRead() {
        // Given
        DatabaseExecutionManager manager = mock(DatabaseExecutionManager.class);
        when(manager.snapshotStats()).thenThrow(new IllegalStateException("boom"));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When / Then
        assertThat(indicator.health().getStatus()).as("unexpected stats failures should surface in health")
                .isEqualTo(Status.DOWN);
    }
}
