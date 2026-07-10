package io.github.gear4jtest.core.event;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;

/**
 * Resolves immutable run-local subscriptions from user and side-compute
 * definitions.
 */
@io.github.gear4jtest.core.api.annotation.Internal
final class EventSubscriptionResolver {
    private EventSubscriptionResolver() {
    }

    static List<EventSubscription<?>> resolve(EventHandlingDefinition definition,
                                              ExecutionContextRegistry registry) {
        List<EventSubscription<?>> subscriptions = new ArrayList<>(definition.getSubscriptions());
        subscriptions.addAll(SideComputeListener.subscriptions(definition.getSideComputers(), registry));
        return List.copyOf(subscriptions);
    }
}
