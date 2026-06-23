package io.github.gear4jtest.core.sidecompute;

import java.util.List;

import io.github.gear4jtest.core.api.annotation.Internal;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;

@Internal
public final class SideComputeListener {
    private SideComputeListener() {
    }

    public static List<EventSubscription<?>> subscriptions(List<SideComputer<?, ?, ?>> computers,
                                                           ExecutionContextRegistry registry) {
        if (computers == null || computers.isEmpty()) {
            return List.of();
        }
        return computers.stream()
                .<EventSubscription<?>>map(computer -> computer.toSubscription(registry))
                .toList();
    }
}
