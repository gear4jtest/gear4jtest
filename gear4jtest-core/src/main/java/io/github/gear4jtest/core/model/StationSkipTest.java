package io.github.gear4jtest.core.model;

@FunctionalInterface
public interface StationSkipTest {

    /**
    * @param input input courant de la station
    * @param stationCtx StationExecutionContext (capabilities : params cache, operator, etc.)
    */
    boolean test(Object input, StationExecutionContext stationCtx);
}
