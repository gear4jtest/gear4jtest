package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Objects;

/** Current liveness/readiness assessment for a persistence runtime. */
public record PersistenceOperationalStatus(boolean live,
                                           boolean ready,
                                           boolean connectivityVerified,
                                           boolean connectivityAvailable,
                                           boolean recoveredAfterFailure,
                                           Reason reason,
                                           Instant observedAt,
                                           PersistenceRuntimeStats stats) {
    public PersistenceOperationalStatus {
        reason = Objects.requireNonNull(reason, "reason must not be null");
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        stats = Objects.requireNonNull(stats, "stats must not be null");
    }

    public enum Reason {
        READY,
        SHUT_DOWN,
        CONNECTIVITY_UNAVAILABLE,
        RECOVERY_PENDING,
        BACKLOG_SIZE_EXCEEDED,
        BACKLOG_AGE_EXCEEDED,
        MONITORING_ONLY
    }
}
