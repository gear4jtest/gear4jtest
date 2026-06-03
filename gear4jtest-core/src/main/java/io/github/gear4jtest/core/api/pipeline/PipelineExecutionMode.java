package io.github.gear4jtest.core.api.pipeline;

/**
 * Defines how a pipeline call station executes its target pipeline.
 */
public enum PipelineExecutionMode {
    /**
     * Executes the child pipeline root station inside the current run.
     */
    INLINE,

    /**
     * Executes the child pipeline as a separate run linked to the parent station.
     */
    NESTED_RUN
}
