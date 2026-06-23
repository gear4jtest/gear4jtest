package io.github.gear4jtest.core.event.transport;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID STATION_EXECUTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");
    private static final UUID PARENT_OPERATION_ID = UUID.fromString("00000000-0000-7000-8000-000000000004");
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void constructor_shouldDefensivelyCopyHeadersAndPayload() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("trace", "a");
        byte[] payload = { 1, 2, 3 };

        // When
        EventEnvelope envelope = envelope(headers, payload);
        headers.put("late", "mutation");
        payload[0] = 9;
        byte[] exposedPayload = envelope.payload();
        exposedPayload[1] = 9;

        // Then
        assertThat(envelope.headers()).containsExactly(Map.entry("trace", "a"));
        assertThat(envelope.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void constructor_shouldNormalizeNullHeadersAndPayload() {
        // When
        EventEnvelope envelope = envelope(null, null);

        // Then
        assertThat(envelope.headers()).isEmpty();
        assertThat(envelope.payload()).isEmpty();
    }

    @Test
    void equalsAndHashCode_shouldUsePayloadContentAndAllEnvelopeFields() {
        // Given
        EventEnvelope envelope = envelope(Map.of("trace", "a"), new byte[] { 1, 2, 3 });
        EventEnvelope sameContent = envelope(Map.of("trace", "a"), new byte[] { 1, 2, 3 });

        // Then
        assertThat(envelope).isEqualTo(envelope).isEqualTo(sameContent).isNotEqualTo(null).isNotEqualTo("event");
        assertThat(envelope.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(envelope).isNotEqualTo(withEventId(UUID.fromString("00000000-0000-7000-8000-000000000099")))
                .isNotEqualTo(withEventType("OTHER"))
                .isNotEqualTo(withAssemblyLineId("other-pipeline"))
                .isNotEqualTo(withExecutionId(UUID.fromString("00000000-0000-7000-8000-000000000098")))
                .isNotEqualTo(withStationExecutionId(UUID.fromString("00000000-0000-7000-8000-000000000097")))
                .isNotEqualTo(withOperationId("other-operation"))
                .isNotEqualTo(withParentOperationId(UUID.fromString("00000000-0000-7000-8000-000000000096")))
                .isNotEqualTo(withItemId("other-item"))
                .isNotEqualTo(withOccurredAt(Instant.parse("2026-06-22T11:00:00Z")))
                .isNotEqualTo(envelope(Map.of("trace", "b"), new byte[] { 1, 2, 3 }))
                .isNotEqualTo(envelope(Map.of("trace", "a"), new byte[] { 1, 2, 4 }))
                .isNotEqualTo(withContentType("application/octet-stream"))
                .isNotEqualTo(withPartitionKey("other-partition"))
                .isNotEqualTo(withSchemaVersion("2"));
    }

    @Test
    void headers_shouldBeImmutable() {
        // Given
        EventEnvelope envelope = envelope(Map.of("trace", "a"), new byte[] { 1 });
        Map<String, String> headers = envelope.headers();

        // When / Then
        assertThatThrownBy(() -> headers.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toString_shouldDescribePayloadWithoutDumpingRawBytes() {
        // Given
        EventEnvelope envelope = envelope(Map.of("trace", "a"), new byte[] { 1, 2, 3 });

        // When
        String description = envelope.toString();

        // Then
        assertThat(description).contains("eventId=" + EVENT_ID)
                .contains("payloadLength=3")
                .contains("payload=byte[3], hash=")
                .doesNotContain("payload=[1, 2, 3]")
                .doesNotContain("[B@");
    }

    private static EventEnvelope withEventId(UUID eventId) {
        return new EventEnvelope(eventId, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withEventType(String eventType) {
        return new EventEnvelope(EVENT_ID, eventType, "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withAssemblyLineId(String assemblyLineId) {
        return new EventEnvelope(EVENT_ID, "TYPE", assemblyLineId, EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withExecutionId(UUID executionId) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", executionId, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withStationExecutionId(UUID stationExecutionId) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, stationExecutionId, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withOperationId(String operationId) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, operationId,
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withParentOperationId(UUID parentOperationId) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                parentOperationId, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withItemId(String itemId) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, itemId, OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withOccurredAt(Instant occurredAt) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", occurredAt, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", "1");
    }

    private static EventEnvelope withContentType(String contentType) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                contentType, "partition", "1");
    }

    private static EventEnvelope withPartitionKey(String partitionKey) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", partitionKey, "1");
    }

    private static EventEnvelope withSchemaVersion(String schemaVersion) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, Map.of("trace", "a"), new byte[] { 1, 2, 3 },
                "application/json", "partition", schemaVersion);
    }

    private static EventEnvelope envelope(Map<String, String> headers, byte[] payload) {
        return new EventEnvelope(EVENT_ID, "TYPE", "pipeline", EXECUTION_ID, STATION_EXECUTION_ID, "operation",
                PARENT_OPERATION_ID, "item", OCCURRED_AT, headers, payload, "application/json", "partition", "1");
    }
}
