package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;

/**
 * Internal strategy collaborator used only by
 * {@link AssemblyLineCallStationStrategy} to launch a child assembly line as a
 * real nested run.
 *
 * <p>
 * This interface intentionally lives outside the public API/context services.
 * User operations and regular runtime extensions should not orchestrate nested
 * assembly line runs directly; they should model such calls explicitly with a
 * {@link AssemblyLineCallStation} so validation, lineage, cycle detection and
 * BO traceability remain centralized.
 * </p>
 */
@FunctionalInterface
public interface NestedAssemblyLineExecutor {
    static NestedAssemblyLineExecutor unsupported() {
        return (station, childAssemblyLine, input, parentContext) -> {
            throw new UnsupportedOperationException(
                    "Nested pipeline execution is not available in this strategy registry");
        };
    }

    ExecutionResult<?> executeNested(AssemblyLineCallStation<?, ?> station,
                                     AssemblyLine<?, ?> childAssemblyLine,
                                     Object input,
                                     StationExecutionContext parentContext);
}
