package io.github.gear4jtest.jdbc.execution;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;

/** Shared admission guard for active run buffers and retained station logs. */
final class PersistenceCapacityGuard {
    private final int maxActiveRuns;
    private final int maxBufferedStationLogs;
    private final AtomicInteger activeRuns = new AtomicInteger();
    private final AtomicInteger bufferedStationLogs = new AtomicInteger();

    PersistenceCapacityGuard(int maxActiveRuns, int maxBufferedStationLogs) {
        if (maxActiveRuns <= 0) {
            throw new IllegalArgumentException("maxActiveRuns must be > 0");
        }
        if (maxBufferedStationLogs <= 0) {
            throw new IllegalArgumentException("maxBufferedStationLogs must be > 0");
        }
        this.maxActiveRuns = maxActiveRuns;
        this.maxBufferedStationLogs = maxBufferedStationLogs;
    }

    void acquireRun(UUID runId) {
        if (!tryAcquire(activeRuns, 1, maxActiveRuns)) {
            throw new ExecutionPersistenceException("Maximum active persistence runs reached. runId=" + runId
                    + ", maxActiveRuns=" + maxActiveRuns);
        }
    }

    void releaseRun() {
        release(activeRuns, 1, "active persistence runs");
    }

    void acquireStationLogs(UUID runId, int count) {
        if (count <= 0) {
            return;
        }
        if (!tryAcquire(bufferedStationLogs, count, maxBufferedStationLogs)) {
            throw new ExecutionPersistenceException("Global station log persistence buffer is full. runId=" + runId
                    + ", maxBufferedStationLogs=" + maxBufferedStationLogs
                    + ", attemptedAppendCount=" + count);
        }
    }

    void releaseStationLogs(int count) {
        if (count > 0) {
            release(bufferedStationLogs, count, "buffered station logs");
        }
    }

    int activeRuns() {
        return activeRuns.get();
    }

    int bufferedStationLogs() {
        return bufferedStationLogs.get();
    }

    private static boolean tryAcquire(AtomicInteger counter, int count, int maximum) {
        while (true) {
            int current = counter.get();
            if (count > maximum - current) {
                return false;
            }
            if (counter.compareAndSet(current, current + count)) {
                return true;
            }
        }
    }

    private static void release(AtomicInteger counter, int count, String resource) {
        int remaining = counter.addAndGet(-count);
        if (remaining < 0) {
            counter.addAndGet(count);
            throw new IllegalStateException("Released more " + resource + " than acquired");
        }
    }
}
