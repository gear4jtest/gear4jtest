package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Registry of active per-run persistence buffers. */
final class OperationRecordBufferRegistry {
    private final ConcurrentMap<UUID, OperationRecordBuffer> buffers = new ConcurrentHashMap<>();
    private final int capacityPerRun;
    private final int defaultFlushThreshold;

    OperationRecordBufferRegistry(int capacityPerRun, int defaultFlushThreshold) {
        this.capacityPerRun = capacityPerRun;
        this.defaultFlushThreshold = defaultFlushThreshold;
    }

    OperationRecordBuffer createFresh(UUID runId, int flushThreshold) {
        OperationRecordBuffer buffer = new OperationRecordBuffer(runId, capacityPerRun, flushThreshold);
        buffers.put(runId, buffer);
        return buffer;
    }

    OperationRecordBuffer getOrCreate(UUID runId) {
        return buffers.computeIfAbsent(runId,
                                       id -> new OperationRecordBuffer(id, capacityPerRun,
                                               defaultFlushThreshold));
    }

    OperationRecordBuffer get(UUID runId) {
        return buffers.get(runId);
    }

    void remove(UUID runId) {
        buffers.remove(runId);
    }

    Collection<OperationRecordBuffer> activeBuffers() {
        return buffers.values();
    }

    int activeRunCount() {
        return buffers.size();
    }

    int bufferedStationLogCount() {
        return buffers.values().stream().mapToInt(OperationRecordBuffer::retainedCount).sum();
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
        buffers.clear();
    }
}
