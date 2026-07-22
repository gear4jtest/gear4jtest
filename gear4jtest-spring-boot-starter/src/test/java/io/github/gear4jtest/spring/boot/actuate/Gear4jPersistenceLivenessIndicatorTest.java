package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Gear4jPersistenceLivenessIndicatorTest {
    @Test
    void health_shouldStayUpWithoutProbingDatabaseReadiness() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.isAlive()).thenReturn(true);
        Gear4jPersistenceLivenessIndicator indicator = new Gear4jPersistenceLivenessIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("probe", "liveness");
        verify(manager, never()).probeHealth();
        verify(manager, never()).snapshotStats();
    }

    @Test
    void health_shouldBeDownAfterPersistenceManagerShutdown() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.isAlive()).thenReturn(false);
        Gear4jPersistenceLivenessIndicator indicator = new Gear4jPersistenceLivenessIndicator(manager);

        // When / Then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
