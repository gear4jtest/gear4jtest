package io.github.gear4jtest.core.event.transport;

import java.util.Objects;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventReaction;

/**
 * Best-effort forwarding reaction from the in-process Gear4J runtime to an
 * external transport.
 *
 * <p>
 * This class is intentionally simple: it maps a runtime event to an
 * {@link EventEnvelope} and publishes it. If the external transport is
 * unavailable, overloaded or rejects the envelope, the failure is surfaced as a
 * reaction failure and handled by the current asynchronous runtime.
 * </p>
 *
 * <p>
 * Important: using this reaction through the current core event runtime does
 * <strong>not</strong> provide durable delivery, retry, replay or exactly-once
 * semantics.
 * </p>
 */
public final class ExternalTransportReaction<T extends Event> implements EventReaction<T> {

    private final EventEnvelopeMapper mapper;
    private final ExternalEventTransport transport;

    public ExternalTransportReaction(EventEnvelopeMapper mapper, ExternalEventTransport transport) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public void handle(T event) throws Exception {
        final EventEnvelope envelope;
        try {
            envelope = mapper.map(event);
        } catch (Exception exception) {
            throw new ExternalTransportPublishException("Failed to map runtime event to external transport envelope.",
                    exception);
        }

        final PublishResult result;
        try {
            result = transport.publish(envelope);
        } catch (Exception exception) {
            throw new ExternalTransportPublishException("Failed to publish event envelope to external transport.",
                    exception);
        }

        if (!result.accepted()) {
            throw new ExternalTransportPublishException(
                    "External transport rejected event envelope: " + result.detail());
        }
    }
}
