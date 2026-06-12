package io.github.gear4jtest.core.event.transport;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {
    @Test
    void constructor_shouldDefensivelyCopyHeadersAndPayload() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("source", "test");
        byte[] payload = new byte[] { 1, 2, 3 };

        // When
        EventEnvelope envelope = envelope(headers, payload);
        headers.put("source", "mutated");
        payload[0] = 99;

        // Then
        assertThat(envelope.headers()).containsEntry("source", "test");
        assertThat(envelope.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void payload_shouldReturnDefensiveCopies() {
        // Given
        EventEnvelope envelope = envelope(Map.of(), new byte[] { 1, 2, 3 });

        // When
        byte[] firstRead = envelope.payload();
        firstRead[0] = 99;

        // Then
        assertThat(envelope.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void headers_shouldBeImmutable() {
        // Given
        EventEnvelope envelope = envelope(Map.of("source", "test"), new byte[] { 1 });

        // When / Then
        assertThatThrownBy(() -> envelope.headers().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static EventEnvelope envelope(Map<String, String> headers, byte[] payload) {
        return new EventEnvelope(UUID.randomUUID(), "TestEvent", "pipeline", UUID.randomUUID(), UUID.randomUUID(),
                "operation", null, "item", Instant.now(), headers, payload, "application/json", "partition", "1");
    }
}
