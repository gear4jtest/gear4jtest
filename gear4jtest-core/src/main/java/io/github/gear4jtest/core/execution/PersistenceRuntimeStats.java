package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.time.Instant;

/**
 * Point-in-time observability snapshot for an asynchronous persistence
 * implementation.
 */
public record PersistenceRuntimeStats(int activeRuns,
                                      int bufferedStationLogs,
                                      long scheduledFlushes,
                                      long completedFlushes,
                                      long failedFlushes,
                                      long rejectedAppends,
                                      Instant observedAt,
                                      Duration oldestBufferedStationLogAge,
                                      Instant lastSuccessfulFlushAt,
                                      Instant lastFailedFlushAt,
                                      Instant lastRejectedAppendAt,
                                      boolean shutdown) {
    public PersistenceRuntimeStats {
        observedAt = java.util.Objects.requireNonNull(observedAt, "observedAt must not be null");
        oldestBufferedStationLogAge = java.util.Objects.requireNonNull(oldestBufferedStationLogAge,
                                                                       "oldestBufferedStationLogAge must not be null");
        if (oldestBufferedStationLogAge.isNegative()) {
            throw new IllegalArgumentException("oldestBufferedStationLogAge must not be negative");
        }
    }

    public PersistenceRuntimeStats(int activeRuns,
                                   int bufferedStationLogs,
                                   long scheduledFlushes,
                                   long completedFlushes,
                                   long failedFlushes,
                                   long rejectedAppends) {
        this(activeRuns, bufferedStationLogs, scheduledFlushes, completedFlushes, failedFlushes, rejectedAppends,
                Instant.now(), Duration.ZERO, null, null, null, false);
    }
}
