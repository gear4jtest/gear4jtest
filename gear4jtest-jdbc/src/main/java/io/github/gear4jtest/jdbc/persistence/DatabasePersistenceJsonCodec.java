package io.github.gear4jtest.jdbc.persistence;

import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;

final class DatabasePersistenceJsonCodec implements PersistenceJsonCodec {
    private final ObjectMapper objectMapper;

    DatabasePersistenceJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to serialize persistence payload", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload as " + clazz.getName(),
                    e);
        }
    }

    @Override
    public <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return json != null ? objectMapper.readValue(json, type) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload", e);
        }
    }
}
