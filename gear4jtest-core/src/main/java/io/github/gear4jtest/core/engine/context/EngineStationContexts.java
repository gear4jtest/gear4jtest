package io.github.gear4jtest.core.engine.context;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.StationTrace;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

public final class EngineStationContexts {
    private EngineStationContexts() {
    }

    public static ExecutionSupport support(StationExecutionContext context) {
        return engineContext(context).getSupport();
    }

    public static <T> void addCapability(StationExecutionContext context, Class<T> type, T instance) {
        engineContext(context).addCapability(type, instance);
    }

    public static StationLogTrace trace(StationExecutionContext context) {
        return engineContext(context).getRecord();
    }

    public static StationLogTrace mutableTrace(StationTrace trace) {
        if (trace instanceof StationLogTrace mutableTrace) {
            return mutableTrace;
        }
        throw new IllegalStateException("StationRunner returned a trace not owned by the engine runtime: "
                + (trace == null ? "null" : trace.getClass().getName()));
    }

    private static EngineStationExecutionContext engineContext(StationExecutionContext context) {
        if (context instanceof EngineStationExecutionContext engineContext) {
            return engineContext;
        }
        throw new IllegalStateException("StationExecutionContext is not owned by the engine runtime: "
                + context.getClass().getName());
    }
}
