package io.github.gear4jtest.jdbc.execution;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;

/** Mutable counters backing {@link PersistenceRuntimeStats}. */
final class PersistenceRuntimeCounters {
    private final AtomicLong scheduledFlushes = new AtomicLong();
    private final AtomicLong completedFlushes = new AtomicLong();
    private final AtomicLong failedFlushes = new AtomicLong();
    private final AtomicLong rejectedAppends = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessfulFlushAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailedFlushAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastRejectedAppendAt = new AtomicReference<>();

    void recordScheduledFlush() {
        scheduledFlushes.incrementAndGet();
    }

    void recordCompletedFlush() {
        completedFlushes.incrementAndGet();
        lastSuccessfulFlushAt.set(Instant.now());
    }

    void recordSuccessfulFlushProgress() {
        lastSuccessfulFlushAt.set(Instant.now());
    }

    void recordFailedFlush() {
        failedFlushes.incrementAndGet();
        lastFailedFlushAt.set(Instant.now());
    }

    void recordRejectedAppend() {
        rejectedAppends.incrementAndGet();
        lastRejectedAppendAt.set(Instant.now());
    }

    PersistenceRuntimeStats snapshot(OperationRecordBufferRegistry buffers) {
        return snapshot(buffers, false);
    }

    PersistenceRuntimeStats snapshot(OperationRecordBufferRegistry buffers, boolean shutdown) {
        Instant observedAt = Instant.now();
        return new PersistenceRuntimeStats(buffers.activeRunCount(), buffers.bufferedStationLogCount(),
                scheduledFlushes.get(), completedFlushes.get(), failedFlushes.get(), rejectedAppends.get(), observedAt,
                buffers.oldestBufferedStationLogAge(observedAt), lastSuccessfulFlushAt.get(), lastFailedFlushAt.get(),
                lastRejectedAppendAt.get(), shutdown);
    }
}
