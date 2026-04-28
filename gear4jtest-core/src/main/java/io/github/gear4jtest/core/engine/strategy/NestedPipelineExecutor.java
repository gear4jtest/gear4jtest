package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.PipelineCallStation;

/**
 * Internal strategy collaborator used only by {@link PipelineCallStationStrategy} to launch a child
 * pipeline as a real nested run.
 *
 * <p>This interface intentionally lives outside the public API/context services. User operations and
 * regular runtime extensions should not orchestrate nested pipeline runs directly; they should model
 * such calls explicitly with a {@link PipelineCallStation} so validation, lineage, cycle detection and
 * BO traceability remain centralized.</p>
 */
@FunctionalInterface
public interface NestedPipelineExecutor {

    ExecutionResult<?> executeNested(
            PipelineCallStation<?, ?> station,
            AssemblyLine<?, ?> childPipeline,
            Object input,
            StationExecutionContext parentContext);

    static NestedPipelineExecutor unsupported() {
        return (station, childPipeline, input, parentContext) -> {
            throw new UnsupportedOperationException("Nested pipeline execution is not available in this strategy registry");
        };
    }
}
