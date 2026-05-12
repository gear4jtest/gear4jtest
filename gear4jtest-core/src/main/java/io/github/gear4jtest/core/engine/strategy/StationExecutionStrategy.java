package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public interface StationExecutionStrategy<S extends AbstractStation<?, ?>> {
    boolean supports(Class<? extends AbstractStation<?, ?>> stationType);

    /**
     * @param runner Le runner appelant, pour permettre la récursion (callback).
     */
    StationLogTrace run(S station, Object input, StationExecutionContext ctx, StationRunner runner);
}
