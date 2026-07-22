package io.github.gear4jtest.spring.boot.actuate;

import java.util.Objects;

import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Process-level liveness indicator that never probes the database. */
public final class Gear4jPersistenceLivenessIndicator implements HealthIndicator {
    private final PersistenceRuntimeMonitor manager;

    public Gear4jPersistenceLivenessIndicator(PersistenceRuntimeMonitor manager) {
        this.manager = Objects.requireNonNull(manager, "manager must not be null");
    }

    @Override
    public Health health() {
        try {
            return (manager.isAlive() ? Health.up() : Health.down())
                    .withDetail("probe", "liveness")
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception).withDetail("probe", "liveness").build();
        }
    }
}
