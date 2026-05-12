package io.github.gear4jtest.core.event.transport;

import java.util.function.Predicate;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;

/**
 * Convenience factory for forwarding subscriptions.
 */
public final class ExternalSubscriptions {
    private ExternalSubscriptions() {
    }

    public static <T extends Event> EventSubscription<T> forward(Class<T> eventType,
                                                                 ExternalEventTransport transport,
                                                                 EventEnvelopeMapper mapper) {
        return EventSubscription.on(eventType, new ExternalTransportReaction<>(mapper, transport));
    }

    public static <T extends Event> EventSubscription<T> forward(Class<T> eventType,
                                                                 Predicate<? super T> predicate,
                                                                 ExternalEventTransport transport,
                                                                 EventEnvelopeMapper mapper) {
        return EventSubscription.on(eventType, predicate, new ExternalTransportReaction<>(mapper, transport));
    }
}
