package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.sidecompute.DefaultSideComputeAccessor;
import io.github.gear4jtest.core.sidecompute.SideComputeAccessor;

/**
 * Input and runtime context available when resolving a station parameter.
 */
public final class ParameterResolutionContext<IN> {
    private final IN item;
    private final ExecutionContext executionContext;
    private final StationExecutionContext stationExecutionContext;
    private final SideComputeAccessor sideComputeAccessor;

    public ParameterResolutionContext(IN item,
                                      ExecutionContext executionContext,
                                      StationExecutionContext stationExecutionContext) {
        this.item = item;
        this.executionContext = executionContext;
        this.stationExecutionContext = stationExecutionContext;
        this.sideComputeAccessor = stationExecutionContext.getCapability(SideComputeAccessor.class)
                .orElseGet(() -> new DefaultSideComputeAccessor(executionContext));
    }

    public IN getItem() {
        return item;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public StationExecutionContext getOperationExecutionContext() {
        return stationExecutionContext;
    }

    public SideComputeAccessor getSideCompute() {
        return sideComputeAccessor;
    }
}
