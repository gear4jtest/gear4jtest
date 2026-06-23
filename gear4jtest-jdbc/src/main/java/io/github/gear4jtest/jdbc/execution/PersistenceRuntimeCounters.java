package io.github.gear4jtest.jdbc.execution;

import java.util.concurrent.atomic.AtomicLong;

import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;

/** Mutable counters backing {@link PersistenceRuntimeStats}. */
final class PersistenceRuntimeCounters {
    private final AtomicLong scheduledFlushes = new AtomicLong();
    private final AtomicLong completedFlushes = new AtomicLong();
    private final AtomicLong failedFlushes = new AtomicLong();
    private final AtomicLong rejectedAppends = new AtomicLong();

    void recordScheduledFlush() {
        scheduledFlushes.incrementAndGet();
    }

    void recordCompletedFlush() {
        completedFlushes.incrementAndGet();
    }

    void recordFailedFlush() {
        failedFlushes.incrementAndGet();
    }

    void recordRejectedAppend() {
        rejectedAppends.incrementAndGet();
    }

    PersistenceRuntimeStats snapshot(OperationRecordBufferRegistry buffers) {
        return new PersistenceRuntimeStats(buffers.activeRunCount(), buffers.bufferedStationLogCount(),
                scheduledFlushes.get(), completedFlushes.get(), failedFlushes.get(), rejectedAppends.get());
    }
}
