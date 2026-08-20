package io.github.gear4jtest.jdbc.execution;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/** Per-run bounded buffer of station logs awaiting JDBC persistence. */
final class OperationRecordBuffer {
    private final UUID runId;
    private final int capacity;
    private final int flushThreshold;
    private final ArrayDeque<StationLogRecord> queue = new ArrayDeque<>();
    private final PersistenceCapacityGuard capacityGuard;
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicInteger retainedCount = new AtomicInteger();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicReference<ExecutionPersistenceException> lastFailure = new AtomicReference<>();
    private final AtomicReference<Exception> finalizationFailure = new AtomicReference<>();
    private final AtomicReference<AssemblyRunRecord> finalRecord = new AtomicReference<>();
    private volatile Instant oldestRetainedAt;

    OperationRecordBuffer(UUID runId, int capacity, int flushThreshold) {
        this(runId, capacity, flushThreshold,
                new PersistenceCapacityGuard(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    OperationRecordBuffer(UUID runId,
                          int capacity,
                          int flushThreshold,
                          PersistenceCapacityGuard capacityGuard) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        if (flushThreshold <= 0) {
            throw new IllegalArgumentException("flushThreshold must be > 0");
        }
        if (flushThreshold > capacity) {
            throw new IllegalArgumentException("flushThreshold must be <= capacity");
        }
        this.flushThreshold = flushThreshold;
        this.capacityGuard = Objects.requireNonNull(capacityGuard, "capacityGuard must not be null");
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

    int flushThreshold() {
        return flushThreshold;
    }

    boolean isClosed() {
        return closed.get();
    }

    void close() {
        closed.set(true);
    }

    void beginFinalization(AssemblyRunRecord record) {
        flushLock.lock();
        try {
            finalRecord.set(Objects.requireNonNull(record, "record must not be null"));
            finalizationFailure.set(null);
            closed.set(true);
        } finally {
            flushLock.unlock();
        }
    }

    boolean isFinalizationPending() {
        return finalRecord.get() != null;
    }

    AssemblyRunRecord finalRecord() {
        AssemblyRunRecord record = finalRecord.get();
        if (record == null) {
            throw new IllegalStateException("No run finalization is pending for runId=" + runId);
        }
        return record;
    }

    void completeFinalization() {
        finalRecord.set(null);
        finalizationFailure.set(null);
    }

    boolean markFlushScheduled() {
        return flushScheduled.compareAndSet(false, true);
    }

    void clearFlushScheduled() {
        flushScheduled.set(false);
    }

    boolean append(StationLogRecord stationLogRecord, PersistenceRuntimeCounters counters) {
        return appendAll(List.of(stationLogRecord), counters);
    }

    boolean appendAll(List<StationLogRecord> stationLogRecords, PersistenceRuntimeCounters counters) {
        if (stationLogRecords == null || stationLogRecords.isEmpty()) {
            return false;
        }
        List<StationLogRecord> records = List.copyOf(stationLogRecords);
        flushLock.lock();
        try {
            if (closed.get()) {
                throw new ExecutionPersistenceException(
                        "Cannot append station logs to a closed run buffer. runId=" + runId
                                + ", stationLogCount=" + records.size());
            }
            if (records.size() > capacity - retainedCount.get()) {
                counters.recordRejectedAppend();
                throw new ExecutionPersistenceException("Station log persistence buffer is full. runId=" + runId
                        + ", maxPendingLogsPerRun=" + capacity
                        + ", attemptedAppendCount=" + records.size());
            }
            try {
                capacityGuard.acquireStationLogs(runId, records.size());
            } catch (ExecutionPersistenceException exception) {
                counters.recordRejectedAppend();
                throw exception;
            }
            boolean added = false;
            try {
                queue.addAll(records);
                retainedCount.addAndGet(records.size());
                pendingCount.addAndGet(records.size());
                markRetained();
                added = true;
            } finally {
                if (!added) {
                    capacityGuard.releaseStationLogs(records.size());
                }
            }
            return pendingCount.get() >= flushThreshold;
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

    List<StationLogRecord> drainBatch() {
        int size = Math.min(flushThreshold, queue.size());
        List<StationLogRecord> batch = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            batch.add(queue.removeFirst());
        }
        if (!batch.isEmpty()) {
            pendingCount.addAndGet(-batch.size());
        }
        return batch;
    }

    void acknowledgeDrainedBatch(List<StationLogRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        releaseRetained(batch.size());
    }

    void quarantineDrainedRecord(StationLogRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        releaseRetained(1);
    }

    void restoreDrainedBatch(List<StationLogRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<StationLogRecord> records = List.copyOf(batch);
        if (records.size() > capacity - pendingCount.get()) {
            ExecutionPersistenceException failure = new ExecutionPersistenceException(
                    "Could not atomically requeue drained station logs after failed persistence flush. runId="
                            + runId + ", stationLogCount=" + records.size());
            recordFailure(failure);
            throw failure;
        }
        List<StationLogRecord> reversed = new ArrayList<>(records);
        Collections.reverse(reversed);
        reversed.forEach(queue::addFirst);
        pendingCount.addAndGet(records.size());
    }

    void recordFailure(Exception failure) {
        lastFailure.set(new ExecutionPersistenceException("Persistence failed for runId=" + runId, failure));
    }

    void clearFailure() {
        lastFailure.set(null);
    }

    ExecutionPersistenceException currentFailure() {
        return lastFailure.get();
    }

    void recordFinalizationFailure(Exception failure) {
        finalizationFailure.set(failure);
    }

    Exception currentFinalizationFailure() {
        return finalizationFailure.get();
    }

    Instant oldestRetainedAt() {
        return oldestRetainedAt;
    }

    private void releaseRetained(int count) {
        int remaining = retainedCount.addAndGet(-count);
        if (remaining < 0) {
            retainedCount.addAndGet(count);
            throw new IllegalStateException("Acknowledged more station logs than retained for runId=" + runId);
        }
        capacityGuard.releaseStationLogs(count);
        if (remaining == 0) {
            oldestRetainedAt = null;
        }
    }

    private void markRetained() {
        if (oldestRetainedAt == null) {
            oldestRetainedAt = Instant.now();
        }
    }
}
