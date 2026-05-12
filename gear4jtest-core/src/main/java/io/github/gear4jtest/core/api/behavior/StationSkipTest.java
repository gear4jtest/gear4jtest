package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

@FunctionalInterface
public interface StationSkipTest {

    /**
     * Evaluates whether the current station execution should be skipped.
     *
     * @param input      current station input
     * @param stationCtx current station execution context
     * @return {@code true} when the station should be skipped
     */
    boolean test(Object input, StationExecutionContext stationCtx);
}
