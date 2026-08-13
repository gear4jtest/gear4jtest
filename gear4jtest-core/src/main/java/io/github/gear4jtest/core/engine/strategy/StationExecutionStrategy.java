package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Strategy used by the engine to execute a specific station type.
 *
 * <p>
 * Implementations should keep station semantics local to the station kind and
 * delegate child station execution through the provided {@link StationRunner}
 * rather than calling strategies directly.
 * </p>
 */
public interface StationExecutionStrategy<S extends AbstractStation<?, ?>> {
    /**
     * Returns whether this strategy can execute stations of the provided type.
     */
    boolean supports(Class<?> stationType);

    /**
     * Executes a station and returns its runtime trace.
     *
     * @param station station definition to execute
     * @param input   input payload for the station
     * @param ctx     station execution context
     * @param runner  runner callback used to execute child stations recursively
     * @return normalized station execution trace
     */
    StationLogTrace run(S station, Object input, StationExecutionContext ctx, StationRunner runner);
}
