package io.github.gear4jtest.core.persistence;

import java.util.Objects;

/** Exposes runtime statistics for a persistence implementation. */
public interface PersistenceRuntimeMonitor {
    PersistenceRuntimeStats snapshotStats();

    /**
     * Subscribes to completed flush attempts when the implementation supports
     * active observations. Monitoring-only implementations return a no-op
     * subscription.
     */
    default PersistenceFlushSubscription subscribeToFlushes(PersistenceFlushObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        return () -> {
            // no active observations for monitoring-only implementations
        };
    }

    /**
     * Lightweight process-level liveness check. It must not call an external
     * system.
     */
    default boolean isAlive() {
        return !snapshotStats().shutdown();
    }

    /**
     * Probes current readiness. Implementations backed by an external system should
     * verify current connectivity instead of deriving readiness from cumulative
     * counters.
     */
    default PersistenceOperationalStatus probeHealth() {
        PersistenceRuntimeStats stats = snapshotStats();
        boolean live = !stats.shutdown();
        return new PersistenceOperationalStatus(live, live, false, false, false,
                live ? PersistenceOperationalStatus.Reason.MONITORING_ONLY
                        : PersistenceOperationalStatus.Reason.SHUT_DOWN,
                stats.observedAt(), stats);
    }
}
