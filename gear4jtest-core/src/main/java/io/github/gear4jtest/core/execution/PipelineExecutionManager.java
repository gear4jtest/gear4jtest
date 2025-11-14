package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public interface PipelineExecutionManager {
    void start(PipelineExecution execution);
    default void append(OperationExecutionRecord record) {}
    default void append(IteratorBatch batch) {}
    default void heartbeat(UUID pipelineId) {}
    void end(PipelineExecution finalExecution);
    default void flush(UUID pipelineId) {}
    default void shutdown() {}
}
