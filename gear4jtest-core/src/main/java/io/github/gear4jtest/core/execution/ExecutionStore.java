package io.github.gear4jtest.core.execution;

import java.util.List;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public interface ExecutionStore {
    void save(PipelineExecution exec);
    void update(PipelineExecution exec);
    default void saveOperationRecords(List<OperationExecutionRecord> records) {}
    default void saveIteratorBatch(IteratorBatch batch) {}
}
