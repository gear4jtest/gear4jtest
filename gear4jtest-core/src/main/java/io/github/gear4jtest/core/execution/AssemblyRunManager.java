package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.StationLogRecord;

public interface AssemblyRunManager {
    void start(AssemblyRunTrace execution);

    default void append(StationLogRecord stationLogRecord) {
        // no-op by default
    }

    default void appendAll(List<StationLogRecord> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    default void heartbeat(UUID runId) {
        // no-op
    }

    default void flush(UUID runId) {
        // no-op by default
    }

    void end(AssemblyRunTrace finalExecution);

    default void shutdown() {
        // no-op by default
    }
}
