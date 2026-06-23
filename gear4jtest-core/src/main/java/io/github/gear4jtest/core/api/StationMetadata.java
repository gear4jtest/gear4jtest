package io.github.gear4jtest.core.api;

import java.util.Map;
import java.util.Optional;

/**
 * Typed metadata attached to a station definition.
 *
 * <p>
 * Metadata is intended for declarative policies and flags that should travel
 * with the station model without adding dedicated fields to every station type.
 * </p>
 */
public interface StationMetadata {
    <T> Optional<T> get(Class<T> type);

    default <T> T require(Class<T> type) {
        return get(type).orElseThrow(() -> new IllegalStateException("Missing station metadata: " + type.getName()));
    }

    static StationMetadata empty() {
        return ImmutableStationMetadata.EMPTY;
    }

    static StationMetadata copyOf(Map<Class<?>, Object> values) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        return new ImmutableStationMetadata(Map.copyOf(values));
    }
}
