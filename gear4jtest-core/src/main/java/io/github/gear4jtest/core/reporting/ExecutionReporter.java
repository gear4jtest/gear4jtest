package io.github.gear4jtest.core.reporting;

import io.github.gear4jtest.core.persistence.PipelineExecution;

public interface ExecutionReporter {
    default void onPipelineStart(PipelineExecution execution) {}
    default void onPipelineUpdate(PipelineExecution execution) {}
    default void onPipelineEnd(PipelineExecution execution) {}
}
