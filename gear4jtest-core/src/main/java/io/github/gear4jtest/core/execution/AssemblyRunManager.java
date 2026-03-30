package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;

public interface AssemblyRunManager {

    void start(AssemblyRun execution);

    default void append(StationLogSnapshot record) {
        // no-op by default
    }

    default void appendAll(List<StationLogSnapshot> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    default void heartbeat(UUID pipelineId) {
        // no-op
    }

    default void flush(UUID pipelineId) {
        // no-op by default
    }

    void end(AssemblyRun finalExecution);

    default void shutdown() {
        // no-op by default
    }
}
