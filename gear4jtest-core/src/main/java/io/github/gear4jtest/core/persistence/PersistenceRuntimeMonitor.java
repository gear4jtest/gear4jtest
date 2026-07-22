package io.github.gear4jtest.core.persistence;

/** Exposes runtime statistics for a persistence implementation. */
public interface PersistenceRuntimeMonitor {
    PersistenceRuntimeStats snapshotStats();

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
