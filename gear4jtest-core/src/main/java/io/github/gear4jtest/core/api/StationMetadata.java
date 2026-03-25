package io.github.gear4jtest.core.api;

import java.util.Optional;

/**
 * Metadata typed attachée à une station (définition), pour porter des policies/flags.
 */
public interface StationMetadata {

    <T> Optional<T> get(Class<T> type);

    default <T> T require(Class<T> type) {
        return get(type)
                .orElseThrow(() -> new IllegalStateException("Missing station metadata: " + type.getName()));
    }
}
