package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.api.context.ExecutionContext;

@FunctionalInterface
public interface SideComputeHandler<T> {

    void handle(
            String sideComputeKey,
            OperationCompletedEvent event,
            T value,
            ExecutionContext executionContext);
}
