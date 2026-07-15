package io.github.gear4jtest.jdbc.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/** Per-run bounded buffer of station logs awaiting JDBC persistence. */
final class OperationRecordBuffer {
    private final UUID runId;
    private final int capacity;
    private final ArrayBlockingQueue<StationLogRecord> queue;
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicInteger retainedCount = new AtomicInteger();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicReference<ExecutionPersistenceException> firstFailure = new AtomicReference<>();
    private final AtomicReference<Exception> finalizationFailure = new AtomicReference<>();
    private volatile Instant oldestRetainedAt;

    OperationRecordBuffer(UUID runId, int capacity) {
        this.runId = runId;
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    UUID runId() {
        return runId;
    }

    int pendingCount() {
        return pendingCount.get();
    }

    int retainedCount() {
        return retainedCount.get();
    }

    boolean isClosed() {
        return closed.get();
    }

    void close() {
        closed.set(true);
    }

    boolean markFlushScheduled() {
        return flushScheduled.compareAndSet(false, true);
    }

    void clearFlushScheduled() {
        flushScheduled.set(false);
    }

    boolean append(StationLogRecord stationLogRecord,
                   int batchSize,
                   PersistenceRuntimeCounters counters) {
        assertHealthy();
        flushLock.lock();
        try {
            assertHealthy();
            if (closed.get()) {
                throw new ExecutionPersistenceException(
                        "Cannot append station log to a closed run buffer. runId=" + runId
                                + ", stationLogId=" + stationLogRecord.id());
            }
            if (!queue.offer(stationLogRecord)) {
                counters.recordRejectedAppend();
                throw new ExecutionPersistenceException("Station log persistence buffer is full. runId=" + runId
                        + ", maxPendingLogsPerRun=" + capacity);
            }
            retainedCount.incrementAndGet();
            markRetained();
            return pendingCount.incrementAndGet() >= batchSize;
        } finally {
            flushLock.unlock();
        }
    }

    boolean appendAll(List<StationLogRecord> stationLogRecords,
                      int batchSize,
                      PersistenceRuntimeCounters counters) {
        if (stationLogRecords == null || stationLogRecords.isEmpty()) {
            return false;
        }
        assertHealthy();
        flushLock.lock();
        try {
            assertHealthy();
            if (closed.get()) {
                throw new ExecutionPersistenceException(
                        "Cannot append station logs to a closed run buffer. runId=" + runId
                                + ", stationLogCount=" + stationLogRecords.size());
            }
            if (queue.remainingCapacity() < stationLogRecords.size()) {
                counters.recordRejectedAppend();
                throw new ExecutionPersistenceException("Station log persistence buffer is full. runId=" + runId
                        + ", maxPendingLogsPerRun=" + capacity
                        + ", attemptedAppendCount=" + stationLogRecords.size());
            }
            for (StationLogRecord stationLogRecord : stationLogRecords) {
                queue.offer(stationLogRecord);
            }
            retainedCount.addAndGet(stationLogRecords.size());
            markRetained();
            return pendingCount.addAndGet(stationLogRecords.size()) >= batchSize;
        } finally {
            flushLock.unlock();
        }
    }

    void lockFlush() {
        flushLock.lock();
    }

    boolean tryLockFlush(long timeoutNanos) throws InterruptedException {
        return flushLock.tryLock(timeoutNanos, TimeUnit.NANOSECONDS);
    }

    void unlockFlush() {
        flushLock.unlock();
    }

    List<StationLogRecord> drainBatch(int batchSize) {
        List<StationLogRecord> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (!batch.isEmpty()) {
            pendingCount.addAndGet(-batch.size());
        }
        return batch;
    }

    void acknowledgeDrainedBatch(List<StationLogRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (retainedCount.addAndGet(-batch.size()) == 0) {
            oldestRetainedAt = null;
        }
    }

    void restoreDrainedBatch(List<StationLogRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (StationLogRecord stationLogRecord : batch) {
            if (!queue.offer(stationLogRecord)) {
                recordFailure(new ExecutionPersistenceException(
                        "Could not requeue drained station log after failed persistence flush. runId=" + runId
                                + ", stationLogId=" + stationLogRecord.id()));
                break;
            }
            pendingCount.incrementAndGet();
        }
    }

    void recordFailure(Exception failure) {
        firstFailure.compareAndSet(null,
                                   new ExecutionPersistenceException("Persistence failed for runId=" + runId,
                                           failure));
    }

    ExecutionPersistenceException currentFailure() {
        return firstFailure.get();
    }

    void recordFinalizationFailure(Exception failure) {
        finalizationFailure.set(failure);
    }

    Exception currentFinalizationFailure() {
        return finalizationFailure.get();
    }

    void clearFinalizationFailure() {
        finalizationFailure.set(null);
    }

    Instant oldestRetainedAt() {
        return oldestRetainedAt;
    }

    private void markRetained() {
        if (oldestRetainedAt == null) {
            oldestRetainedAt = Instant.now();
        }
    }

    void assertHealthy() {
        ExecutionPersistenceException failure = firstFailure.get();
        if (failure != null) {
            throw failure;
        }
    }
}
