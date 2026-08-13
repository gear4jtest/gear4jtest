package io.github.gear4jtest.jdbc.persistence;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabasePersistenceJsonCodecTest {
    @Test
    void jsonCodec_shouldRoundTripObjectsAndHandleNullValues() {
        // Given
        DatabasePersistenceJsonCodec codec = new DatabasePersistenceJsonCodec(new ObjectMapper());

        // When
        String json = codec.toJson(Map.of("token", "abc", "count", 2));

        // Then
        assertThat(json).contains("token").contains("count");
        assertThat(codec.fromJson(json, new TypeReference<Map<String, Object>>() {}))
                .containsEntry("token", "abc")
                .containsEntry("count", 2);
        assertThat(codec.fromJson("{\"name\":\"gear\"}", Payload.class).name()).isEqualTo("gear");
        assertThat(codec.toJson(null)).isNull();
        assertThat(codec.fromJson(null, Payload.class)).isNull();
        assertThat(codec.fromJson(null, new TypeReference<Map<String, Object>>() {})).isNull();
    }

    @Test
    void jacksonFactory_shouldHonorApplicationModulesForBusinessTypes() {
        // Given
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PersistenceJsonCodec codec = PersistenceJsonCodec.jackson(objectMapper);
        BusinessPayload payload = new BusinessPayload("order-42", Instant.parse("2026-07-24T08:15:30Z"));

        // When
        String json = codec.toJson(payload);
        BusinessPayload restored = codec.fromJson(json, BusinessPayload.class);

        // Then
        assertThat(restored).isEqualTo(payload);
    }

    @Test
    void jsonCodec_shouldWrapSerializationAndDeserializationFailures() throws JsonProcessingException {
        // Given
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(failingMapper.writeValueAsString(org.mockito.Mockito.any()))
                .thenThrow(new IllegalStateException("write failed"));
        org.mockito.Mockito
                .when(failingMapper.readValue(org.mockito.Mockito.anyString(), org.mockito.Mockito.eq(Payload.class)))
                .thenThrow(new IllegalStateException("read class failed"));
        org.mockito.Mockito
                .when(failingMapper.readValue(org.mockito.Mockito.anyString(),
                                              org.mockito.ArgumentMatchers
                                                      .<TypeReference<Map<String, Object>>>any()))
                .thenThrow(new IllegalStateException("read type failed"));
        DatabasePersistenceJsonCodec codec = new DatabasePersistenceJsonCodec(failingMapper);

        // When / Then
        assertThatThrownBy(() -> codec.toJson(Map.of("k", "v")))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessage("Failed to serialize persistence payload")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> codec.fromJson("{}", Payload.class))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessage("Failed to deserialize persistence payload as " + Payload.class.getName())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> codec.fromJson("{}", new TypeReference<Map<String, Object>>() {}))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessage("Failed to deserialize persistence payload")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private record Payload(String name) {}

    private record BusinessPayload(String orderId, Instant createdAt) {}
}
