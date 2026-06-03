package io.github.gear4jtest.core.event.durable;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.transport.EventEnvelope;
import io.github.gear4jtest.core.event.transport.EventEnvelopeMapper;

/** Persists runtime events as durable transport envelopes. */
public final class DurableEventPublisher {
    private final EventEnvelopeMapper mapper;
    private final DurableEventEnvelopeStore store;

    public DurableEventPublisher(EventEnvelopeMapper mapper, DurableEventEnvelopeStore store) {
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
        this.store = java.util.Objects.requireNonNull(store, "store must not be null");
    }

    public StoredEventEnvelope publish(Event event) throws Exception {
        EventEnvelope envelope = mapper.map(event);
        return store.append(envelope);
    }
}
