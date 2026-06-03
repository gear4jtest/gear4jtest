package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Extension point used to wrap station execution.
 *
 * <p>
 * Wrappers are part of the station runner chain and therefore execute inside
 * normal station runtime semantics. Use this SPI for behavior that must be in
 * the execution path. Use {@link StationLifecycleExtension} for passive
 * observation of normalized station outcomes.
 * </p>
 */
public interface StationWrapperExtension extends RuntimeExtension {
    /**
     * Decorates the station runner used for a run.
     *
     * @param delegate runner to call from the wrapper
     * @param ctx      execution context of the current run
     * @return the decorated runner
     */
    StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx);
}
