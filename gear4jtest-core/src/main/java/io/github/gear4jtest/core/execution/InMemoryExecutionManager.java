package io.github.gear4jtest.core.execution;

import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class InMemoryExecutionManager implements PipelineExecutionManager {

    @Override
    public void append(io.github.gear4jtest.core.persistence.OperationExecutionRecord record) {
        var id = java.util.UUID.fromString(record.getPipelineExecutionId());
        io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository.INSTANCE.findById(id).ifPresent(exec -> {
            if (exec.getOperations() == null) exec.setOperations(new java.util.ArrayList<>());
            exec.getOperations().add(record);
        });
    }


    @Override
    public void start(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.save(execution);
    }

    @Override
    public void end(PipelineExecution finalExecution) {
        InMemoryPipelineExecutionRepository.INSTANCE.update(finalExecution);
    }
}
