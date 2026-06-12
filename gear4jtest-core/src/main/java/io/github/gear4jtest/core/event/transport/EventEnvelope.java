package io.github.gear4jtest.core.event.transport;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stable transport contract for forwarding Gear4J events outside the in-process
 * runtime.
 *
 * <p>
 * This envelope is intentionally transport-friendly and does not expose
 * arbitrary Java objects. Payload serialization is the responsibility of the
 * {@link EventEnvelopeMapper}.
 * </p>
 *
 * <p>
 * The contract is defensively immutable: headers are copied and payload bytes
 * are cloned on construction and on access. This matters for outbox/retry
 * scenarios where a previously accepted envelope must not be mutated by caller
 * code after it has been persisted or handed to a transport.
 * </p>
 */
public record EventEnvelope(UUID eventId,
                            String eventType,
                            String pipelineId,
                            UUID executionId,
                            UUID stationExecutionId,
                            String operationId,
                            UUID parentOperationId,
                            String itemId,
                            Instant occurredAt,
                            Map<String, String> headers,
                            byte[] payload,
                            String contentType,
                            String partitionKey,
                            String schemaVersion) {
    public EventEnvelope {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
