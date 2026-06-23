package io.github.gear4jtest.core.api.assemblyline;

import java.util.Objects;

/**
 * Declares a runtime capability required or provided by an assembly line
 * runtime contract.
 */
public record RuntimeRequirement(RuntimeRequirementType type, String key) {
    public RuntimeRequirement {
        Objects.requireNonNull(type, "type must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    public static RuntimeRequirement eventHandling(String key) {
        return new RuntimeRequirement(RuntimeRequirementType.EVENT_HANDLING, key);
    }

    public static RuntimeRequirement defaultEventHandling() {
        return eventHandling("default");
    }

    public static RuntimeRequirement stationExtension(Class<?> extensionType) {
        Objects.requireNonNull(extensionType, "extensionType must not be null");
        return new RuntimeRequirement(RuntimeRequirementType.STATION_EXTENSION, extensionType.getName());
    }

    public static RuntimeRequirement custom(String key) {
        return new RuntimeRequirement(RuntimeRequirementType.CUSTOM, key);
    }
}
