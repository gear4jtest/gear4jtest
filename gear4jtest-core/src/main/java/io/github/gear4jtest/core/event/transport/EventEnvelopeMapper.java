package io.github.gear4jtest.core.event.transport;

import io.github.gear4jtest.core.event.Event;

/**
 * Maps an in-process runtime event to a stable external transport envelope.
 *
 * <p>The mapper is responsible for payload serialization and schema selection.</p>
 */
public interface EventEnvelopeMapper {

    EventEnvelope map(Event event) throws Exception;
}
