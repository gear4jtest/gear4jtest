package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Spring Boot Actuator health indicator for Gear4J JDBC persistence. */
public final class Gear4jPersistenceHealthIndicator implements HealthIndicator {
    private final PersistenceRuntimeMonitor manager;

    public Gear4jPersistenceHealthIndicator(PersistenceRuntimeMonitor manager) {
        this.manager = java.util.Objects.requireNonNull(manager, "manager must not be null");
    }

    @Override
    public Health health() {
        try {
            PersistenceRuntimeStats stats = manager.snapshotStats();
            Health.Builder health = stats.failedFlushes() > 0 || stats.rejectedAppends() > 0
                    ? Health.down()
                    : Health.up();
            return health.withDetail("activeRuns", stats.activeRuns())
                    .withDetail("bufferedStationLogs", stats.bufferedStationLogs())
                    .withDetail("scheduledFlushes", stats.scheduledFlushes())
                    .withDetail("completedFlushes", stats.completedFlushes())
                    .withDetail("failedFlushes", stats.failedFlushes())
                    .withDetail("rejectedAppends", stats.rejectedAppends())
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
