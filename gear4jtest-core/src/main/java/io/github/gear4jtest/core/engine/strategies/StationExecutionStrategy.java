package io.github.gear4jtest.core.engine.strategies;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public interface StationExecutionStrategy<S extends AbstractStation> {
    boolean supports(Class<? extends AbstractStation> stationType);

    /**
     * @param runner Le runner appelant, pour permettre la récursion (callback).
     */
    StationLog run(S station, Object input, StationExecutionContext ctx, StationRunner runner);
}