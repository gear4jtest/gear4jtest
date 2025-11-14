package io.github.gear4jtest.core.execution;

import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class InMemoryExecutionManager implements PipelineExecutionManager {

    @Override
    public void start(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.save(execution);
    }

    @Override
    public void end(PipelineExecution finalExecution) {
        InMemoryPipelineExecutionRepository.INSTANCE.update(finalExecution);
    }
}
