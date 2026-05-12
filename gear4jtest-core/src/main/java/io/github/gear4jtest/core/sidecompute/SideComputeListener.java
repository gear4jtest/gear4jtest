package io.github.gear4jtest.core.sidecompute;

import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;

public final class SideComputeListener {

    private SideComputeListener() {
    }

    public static List<EventSubscription<?>> subscriptions(List<SideComputer<?, ?, ?>> computers,
                                                           ExecutionContextRegistry registry) {
        if (computers == null || computers.isEmpty()) {
            return List.of();
        }
        return computers.stream().map(computer -> computer.toSubscription(registry))
                .map(subscription -> (EventSubscription<?>) subscription).collect(Collectors.toList());
    }
}
