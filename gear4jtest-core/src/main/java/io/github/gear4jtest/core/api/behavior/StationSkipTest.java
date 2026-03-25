package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

@FunctionalInterface
public interface StationSkipTest {

    /**
    * @param input input courant de la station
    * @param stationCtx StationExecutionContext (capabilities : params cache, operator, etc.)
    */
    boolean test(Object input, StationExecutionContext stationCtx);
}
