package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
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
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicReference<ExecutionPersistenceException> firstFailure = new AtomicReference<>();

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
            return pendingCount.incrementAndGet() >= batchSize;
        } finally {
            flushLock.unlock();
        }
    }

    void lockFlush() {
        flushLock.lock();
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

    void assertHealthy() {
        ExecutionPersistenceException failure = firstFailure.get();
        if (failure != null) {
            throw failure;
        }
    }
}
