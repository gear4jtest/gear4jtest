package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.event.Event;

@FunctionalInterface
public interface SideComputeHandler<E extends Event, T> {

    void handle(String sideComputeKey, E event, T value, ExecutionContext executionContext);
}
