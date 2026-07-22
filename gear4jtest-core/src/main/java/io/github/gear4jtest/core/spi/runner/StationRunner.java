package io.github.gear4jtest.core.spi.runner;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.trace.StationTrace;

/**
 * Executes a single station through the configured runner chain.
 *
 * <p>
 * This is the internal composition point used by station wrappers, scope
 * initializers, exception boundaries and terminal strategy dispatch.
 * Implementations should return a normalized station trace unless they
 * intentionally propagate a fatal runtime exception.
 * </p>
 */
@FunctionalInterface
public interface StationRunner {
    /**
     * Executes the provided station with the provided input.
     */
    StationTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx);
}
