package io.github.gear4jtest.jdbc.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes and deserializes values stored in Gear4J JDBC JSON columns.
 *
 * <p>
 * Implementations must be thread-safe because independent execution runs may
 * use a repository concurrently.
 * </p>
 */
public interface PersistenceJsonCodec {
    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);

    <T> T fromJson(String json, TypeReference<T> type);

    /**
     * Creates the standard Jackson-backed codec with an application-configured
     * mapper.
     */
    static PersistenceJsonCodec jackson(ObjectMapper objectMapper) {
        return new DatabasePersistenceJsonCodec(objectMapper);
    }
}
