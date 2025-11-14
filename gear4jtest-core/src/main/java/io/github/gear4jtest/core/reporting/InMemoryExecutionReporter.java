package io.github.gear4jtest.core.reporting;

import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class InMemoryExecutionReporter implements ExecutionReporter {
    @Override
    public void onPipelineStart(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.save(execution);
    }

    @Override
    public void onPipelineUpdate(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.update(execution);
    }

    @Override
    public void onPipelineEnd(PipelineExecution execution) {
        InMemoryPipelineExecutionRepository.INSTANCE.update(execution);
    }
}
