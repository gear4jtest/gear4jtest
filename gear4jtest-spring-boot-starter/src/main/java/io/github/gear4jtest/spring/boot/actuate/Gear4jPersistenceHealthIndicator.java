package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.persistence.PersistenceOperationalStatus;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Spring Boot Actuator readiness indicator for Gear4J JDBC persistence. */
public final class Gear4jPersistenceHealthIndicator implements HealthIndicator {
    private final PersistenceRuntimeMonitor manager;

    public Gear4jPersistenceHealthIndicator(PersistenceRuntimeMonitor manager) {
        this.manager = java.util.Objects.requireNonNull(manager, "manager must not be null");
    }

    @Override
    public Health health() {
        try {
            PersistenceOperationalStatus status = manager.probeHealth();
            PersistenceRuntimeStats stats = status.stats();
            Health.Builder health = status.ready() ? Health.up() : Health.down();
            health.withDetail("probe", "readiness")
                    .withDetail("reason", status.reason().name())
                    .withDetail("connectivityVerified", status.connectivityVerified())
                    .withDetail("connectivityAvailable", status.connectivityAvailable())
                    .withDetail("recoveredAfterFailure", status.recoveredAfterFailure())
                    .withDetail("activeRuns", stats.activeRuns())
                    .withDetail("bufferedStationLogs", stats.bufferedStationLogs())
                    .withDetail("oldestBufferedStationLogAgeMillis",
                                stats.oldestBufferedStationLogAge().toMillis())
                    .withDetail("scheduledFlushes", stats.scheduledFlushes())
                    .withDetail("completedFlushes", stats.completedFlushes())
                    .withDetail("failedFlushes", stats.failedFlushes())
                    .withDetail("rejectedAppends", stats.rejectedAppends())
                    .withDetail("quarantinedStationLogs", stats.quarantinedStationLogs());
            addTimestamp(health, "lastSuccessfulFlushAt", stats.lastSuccessfulFlushAt());
            addTimestamp(health, "lastFailedFlushAt", stats.lastFailedFlushAt());
            addTimestamp(health, "lastRejectedAppendAt", stats.lastRejectedAppendAt());
            return health.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    private static void addTimestamp(Health.Builder health, String name, java.time.Instant timestamp) {
        if (timestamp != null) {
            health.withDetail(name, timestamp);
        }
    }
}
