package io.github.gear4jtest.spring.boot.actuate;

import java.time.Duration;
import java.time.Instant;

import io.github.gear4jtest.core.execution.PersistenceOperationalStatus;
import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gear4jPersistenceHealthIndicatorTest {
    @Test
    void health_shouldExposeCurrentReadinessAndRuntimeStats() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        PersistenceRuntimeStats stats = stats(2, 3, 4, 5, 6, 7,
                                              Instant.parse("2026-07-12T18:00:02Z"),
                                              Instant.parse("2026-07-12T18:00:01Z"));
        when(manager.probeHealth()).thenReturn(status(true, true, true,
                                                      PersistenceOperationalStatus.Reason.READY, stats));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("probe", "readiness")
                .containsEntry("reason", "READY")
                .containsEntry("connectivityVerified", true)
                .containsEntry("connectivityAvailable", true)
                .containsEntry("recoveredAfterFailure", true)
                .containsEntry("activeRuns", 2)
                .containsEntry("bufferedStationLogs", 3)
                .containsEntry("failedFlushes", 6L)
                .containsEntry("rejectedAppends", 7L);
    }

    @Test
    void health_shouldBeDownWhileCurrentConnectivityIsUnavailable() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        PersistenceRuntimeStats stats = stats(1, 2, 3, 4, 1, 0, null,
                                              Instant.parse("2026-07-12T18:00:01Z"));
        when(manager.probeHealth()).thenReturn(status(false, false, false,
                                                      PersistenceOperationalStatus.Reason.CONNECTIVITY_UNAVAILABLE,
                                                      stats));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When
        var health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "CONNECTIVITY_UNAVAILABLE")
                .containsEntry("connectivityAvailable", false);
    }

    @Test
    void health_shouldReturnUpAfterTransientFailureHasRecovered() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        Instant failedAt = Instant.parse("2026-07-12T18:00:01Z");
        PersistenceRuntimeStats pending = stats(1, 2, 3, 2, 1, 0, null, failedAt);
        PersistenceRuntimeStats recovered = stats(1, 0, 4, 3, 1, 0, failedAt.plusSeconds(1), failedAt);
        when(manager.probeHealth())
                .thenReturn(status(false, true, false, PersistenceOperationalStatus.Reason.RECOVERY_PENDING, pending))
                .thenReturn(status(true, true, true, PersistenceOperationalStatus.Reason.READY, recovered));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When / Then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        var recoveredHealth = indicator.health();
        assertThat(recoveredHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(recoveredHealth.getDetails()).containsEntry("recoveredAfterFailure", true)
                .containsEntry("failedFlushes", 1L);
    }

    @Test
    void health_shouldBeDownWhenProbeCannotBeRun() {
        // Given
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.probeHealth()).thenThrow(new IllegalStateException("boom"));
        Gear4jPersistenceHealthIndicator indicator = new Gear4jPersistenceHealthIndicator(manager);

        // When / Then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private static PersistenceOperationalStatus status(boolean ready,
                                                       boolean connectivityAvailable,
                                                       boolean recovered,
                                                       PersistenceOperationalStatus.Reason reason,
                                                       PersistenceRuntimeStats stats) {
        return new PersistenceOperationalStatus(true, ready, true, connectivityAvailable, recovered, reason,
                stats.observedAt(), stats);
    }

    private static PersistenceRuntimeStats stats(int activeRuns,
                                                 int bufferedStationLogs,
                                                 long scheduledFlushes,
                                                 long completedFlushes,
                                                 long failedFlushes,
                                                 long rejectedAppends,
                                                 Instant lastSuccessfulFlushAt,
                                                 Instant lastFailedFlushAt) {
        return new PersistenceRuntimeStats(activeRuns, bufferedStationLogs, scheduledFlushes, completedFlushes,
                failedFlushes, rejectedAppends, Instant.parse("2026-07-12T18:00:03Z"), Duration.ofSeconds(2),
                lastSuccessfulFlushAt, lastFailedFlushAt, null, false);
    }
}
