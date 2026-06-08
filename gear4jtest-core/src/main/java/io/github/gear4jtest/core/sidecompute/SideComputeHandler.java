package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.event.Event;

/**
 * Observer invoked after a side-compute value has been produced and before the
 * mapped value is completed in the run-scoped side-compute future.
 *
 * <p>
 * Handlers run inside the event reaction that triggered the side computation.
 * They must be fast, non-blocking where possible, and thread-safe if reused
 * across runs. A thrown exception fails the side-compute future and will be
 * observed later by waiting stations.
 * </p>
 */
@FunctionalInterface
public interface SideComputeHandler<E extends Event, T> {
    /**
     * Handles a raw side-compute value for the current execution context.
     */
    void handle(String sideComputeKey, E event, T value, ExecutionContext executionContext);
}
