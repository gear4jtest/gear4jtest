package io.github.gear4jtest.core.engine.context;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;

public interface EngineStationExecutionContext extends StationExecutionContext {
    ExecutionSupport getSupport();

    <T> void addCapability(Class<T> type, T instance);
}
