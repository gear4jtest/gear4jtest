package io.github.gear4jtest.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;

final class DatabasePersistenceJsonCodec {
    private final ObjectMapper objectMapper;

    DatabasePersistenceJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to serialize persistence payload", e);
        }
    }

    <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload as " + clazz.getName(),
                    e);
        }
    }

    <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return json != null ? objectMapper.readValue(json, type) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload", e);
        }
    }
}
