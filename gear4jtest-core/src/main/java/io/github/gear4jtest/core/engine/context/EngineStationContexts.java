package io.github.gear4jtest.core.engine.context;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;

public final class EngineStationContexts {
    private EngineStationContexts() {
    }

    public static ExecutionSupport support(StationExecutionContext context) {
        return engineContext(context).getSupport();
    }

    public static <T> void addCapability(StationExecutionContext context, Class<T> type, T instance) {
        engineContext(context).addCapability(type, instance);
    }

    private static EngineStationExecutionContext engineContext(StationExecutionContext context) {
        if (context instanceof EngineStationExecutionContext engineContext) {
            return engineContext;
        }
        throw new IllegalStateException("StationExecutionContext is not owned by the engine runtime: "
                + context.getClass().getName());
    }
}
