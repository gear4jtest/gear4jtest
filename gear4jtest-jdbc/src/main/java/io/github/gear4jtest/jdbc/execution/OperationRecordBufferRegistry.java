package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/** Registry of active per-run persistence buffers. */
final class OperationRecordBufferRegistry {
    private final ConcurrentMap<UUID, OperationRecordBuffer> buffers = new ConcurrentHashMap<>();
    private final int capacityPerRun;
    private final int defaultFlushThreshold;
    private final PersistenceCapacityGuard capacityGuard;

    OperationRecordBufferRegistry(int capacityPerRun, int defaultFlushThreshold) {
        this(capacityPerRun, defaultFlushThreshold,
                new PersistenceCapacityGuard(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    OperationRecordBufferRegistry(int capacityPerRun,
                                  int defaultFlushThreshold,
                                  PersistenceCapacityGuard capacityGuard) {
        this.capacityPerRun = capacityPerRun;
        this.defaultFlushThreshold = defaultFlushThreshold;
        this.capacityGuard = capacityGuard;
    }

    OperationRecordBuffer createFresh(UUID runId, int flushThreshold) {
        capacityGuard.acquireRun(runId);
        boolean registered = false;
        try {
            OperationRecordBuffer buffer = new OperationRecordBuffer(runId, capacityPerRun, flushThreshold,
                    capacityGuard);
            OperationRecordBuffer existing = buffers.putIfAbsent(runId, buffer);
            if (existing != null) {
                throw new ExecutionPersistenceException("Persistence buffer already exists for runId=" + runId);
            }
            registered = true;
            return buffer;
        } finally {
            if (!registered) {
                capacityGuard.releaseRun();
            }
        }
    }

    OperationRecordBuffer getOrCreate(UUID runId) {
        OperationRecordBuffer existing = buffers.get(runId);
        if (existing != null) {
            return existing;
        }
        capacityGuard.acquireRun(runId);
        OperationRecordBuffer created;
        try {
            created = new OperationRecordBuffer(runId, capacityPerRun, defaultFlushThreshold, capacityGuard);
        } catch (RuntimeException exception) {
            capacityGuard.releaseRun();
            throw exception;
        }
        OperationRecordBuffer raced = buffers.putIfAbsent(runId, created);
        if (raced != null) {
            capacityGuard.releaseRun();
            return raced;
        }
        return created;
    }

    AppendResult appendAll(UUID runId,
                           List<StationLogRecord> records,
                           PersistenceRuntimeCounters counters) {
        OperationRecordBuffer existing = buffers.get(runId);
        if (existing != null) {
            return new AppendResult(existing, existing.appendAll(records, counters));
        }
        AtomicReference<AppendResult> result = new AtomicReference<>();
        OperationRecordBuffer resolved = buffers.compute(runId, (id, current) -> {
            if (current != null) {
                return current;
            }
            try {
                capacityGuard.acquireRun(id);
            } catch (ExecutionPersistenceException exception) {
                counters.recordRejectedAppend();
                throw exception;
            }
            boolean registered = false;
            try {
                OperationRecordBuffer created = new OperationRecordBuffer(id, capacityPerRun,
                        defaultFlushThreshold, capacityGuard);
                boolean flushRequired = created.appendAll(records, counters);
                result.set(new AppendResult(created, flushRequired));
                registered = true;
                return created;
            } finally {
                if (!registered) {
                    capacityGuard.releaseRun();
                }
            }
        });
        AppendResult createdResult = result.get();
        return createdResult != null
                ? createdResult
                : new AppendResult(resolved, resolved.appendAll(records, counters));
    }

    OperationRecordBuffer get(UUID runId) {
        return buffers.get(runId);
    }

    boolean remove(UUID runId) {
        OperationRecordBuffer buffer = buffers.get(runId);
        if (buffer == null) {
            return false;
        }
        buffer.lockFlush();
        try {
            if (buffer.retainedCount() > 0 || buffer.isFinalizationPending()) {
                return false;
            }
            buffer.close();
            if (!buffers.remove(runId, buffer)) {
                return false;
            }
            capacityGuard.releaseRun();
            return true;
        } finally {
            buffer.unlockFlush();
        }
    }

    Collection<OperationRecordBuffer> activeBuffers() {
        return buffers.values();
    }

    int activeRunCount() {
        return capacityGuard.activeRuns();
    }

    int bufferedStationLogCount() {
        return capacityGuard.bufferedStationLogs();
    }

    Duration oldestBufferedStationLogAge(Instant now) {
        return buffers.values().stream()
                .map(OperationRecordBuffer::oldestRetainedAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .map(oldest -> Duration.between(oldest, now))
                .map(age -> age.isNegative() ? Duration.ZERO : age)
                .orElse(Duration.ZERO);
    }

    void clear() {
        for (UUID runId : buffers.keySet()) {
            remove(runId);
        }
    }

    record AppendResult(OperationRecordBuffer buffer, boolean flushRequired) {}
}
