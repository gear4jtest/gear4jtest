package io.github.gear4jtest.core.api.assemblyline;

/**
 * Defines how a pipeline call station executes its target pipeline.
 */
public enum AssemblyLineExecutionMode {
    /**
     * Executes the child assembly line root station inside the current run.
     */
    INLINE,

    /**
     * Executes the child assembly line as a separate run linked to the parent
     * station.
     */
    NESTED_RUN
}
