package io.github.gear4jtest.core.reporting;

import io.github.gear4jtest.core.persistence.PipelineExecution;

public class NoopExecutionReporter implements ExecutionReporter {
    @Override public void onPipelineStart(PipelineExecution execution) {}
    @Override public void onPipelineUpdate(PipelineExecution execution) {}
    @Override public void onPipelineEnd(PipelineExecution execution) {}
}
