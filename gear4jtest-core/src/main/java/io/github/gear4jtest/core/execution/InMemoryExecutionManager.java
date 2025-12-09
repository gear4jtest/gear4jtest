package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class InMemoryExecutionManager implements PipelineExecutionManager {

    @Override
    public void start(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.save(execution);
    }

    @Override
    public void append(OperationExecutionRecord record) {
        if (record == null) {
            return;
        }
        UUID id = UUID.fromString(record.getPipelineExecutionId());
        InMemoryPipelineExecutionRepository.INSTANCE.findById(id).ifPresent(exec -> {
            List<OperationExecutionRecord> ops = exec.getOperations();
            if (ops == null) {
                ops = new ArrayList<>();
                exec.setOperations(ops);
            }
            ops.add(record);
        });
    }

    @Override
    public void appendAll(List<OperationExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::append);
    }

    @Override
    public void end(PipelineExecution finalExecution) {
        InMemoryPipelineExecutionRepository.INSTANCE.update(finalExecution);
    }
}
