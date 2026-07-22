package io.github.gear4jtest.core.engine.context;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

public interface EngineStationExecutionContext extends StationExecutionContext {
    @Override
    StationLogTrace getRecord();

    ExecutionSupport getSupport();

    <T> void addCapability(Class<T> type, T instance);
}
