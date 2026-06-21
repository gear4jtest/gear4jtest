package io.github.gear4jtest.core.event.transport;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventEnvelope that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(pipelineId, that.pipelineId)
                && Objects.equals(executionId, that.executionId)
                && Objects.equals(stationExecutionId, that.stationExecutionId)
                && Objects.equals(operationId, that.operationId)
                && Objects.equals(parentOperationId, that.parentOperationId)
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(occurredAt, that.occurredAt)
                && Objects.equals(headers, that.headers)
                && Arrays.equals(payload, that.payload)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(partitionKey, that.partitionKey)
                && Objects.equals(schemaVersion, that.schemaVersion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(eventId, eventType, pipelineId, executionId, stationExecutionId, operationId,
                                  parentOperationId, itemId, occurredAt, headers, contentType, partitionKey,
                                  schemaVersion);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "EventEnvelope["
                + "eventId=" + eventId
                + ", eventType=" + eventType
                + ", pipelineId=" + pipelineId
                + ", executionId=" + executionId
                + ", stationExecutionId=" + stationExecutionId
                + ", operationId=" + operationId
                + ", parentOperationId=" + parentOperationId
                + ", itemId=" + itemId
                + ", occurredAt=" + occurredAt
                + ", headers=" + headers
                + ", payloadLength=" + payload.length
                + ", payload=" + payloadDescription()
                + ", contentType=" + contentType
                + ", partitionKey=" + partitionKey
                + ", schemaVersion=" + schemaVersion
                + ']';
    }

    private String payloadDescription() {
        return "byte[" + payload.length + "], hash=" + Arrays.hashCode(payload);
    }
}
