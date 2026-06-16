package io.github.gear4jtest.core.execution;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Registry of active per-run persistence buffers. */
final class OperationRecordBufferRegistry {
    private final ConcurrentMap<UUID, OperationRecordBuffer> buffers = new ConcurrentHashMap<>();
    private final int capacityPerRun;

    OperationRecordBufferRegistry(int capacityPerRun) {
        this.capacityPerRun = capacityPerRun;
    }

    OperationRecordBuffer createFresh(UUID runId) {
        OperationRecordBuffer buffer = new OperationRecordBuffer(runId, capacityPerRun);
        buffers.put(runId, buffer);
        return buffer;
    }

    OperationRecordBuffer getOrCreate(UUID runId) {
        return buffers.computeIfAbsent(runId, id -> new OperationRecordBuffer(id, capacityPerRun));
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
        return buffers.values().stream().mapToInt(OperationRecordBuffer::pendingCount).sum();
    }

    void clear() {
        buffers.clear();
    }
}
