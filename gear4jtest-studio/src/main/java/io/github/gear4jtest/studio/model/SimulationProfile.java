package io.github.gear4jtest.studio.model;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Complete, declarative node substitutions. No executable code or resource
 * references.
 */
public record SimulationProfile(int schemaVersion, Map<String, Mock> mocks) {
    public SimulationProfile {
        if (schemaVersion != 1 || mocks == null || mocks.size() > 32)
            throw new IllegalArgumentException("Invalid simulation profile");
        mocks = java.util.Collections.unmodifiableMap(new TreeMap<>(mocks));
        int size = 0;
        for (var entry : mocks.entrySet()) {
            if (!entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}"))
                throw new IllegalArgumentException("Invalid mock node");
            Objects.requireNonNull(entry.getValue());
            size += entry.getValue().value() == null ? 0 : entry.getValue().value().length();
        }
        if (size > 16384)
            throw new IllegalArgumentException("Mock data exceeds limits");
    }

    public enum Behavior {
        RETURN, PASSTHROUGH, FAIL
    }

    public enum Scalar {
        STRING, INTEGER, LONG, DECIMAL, BOOLEAN
    }

    public record Mock(String capability, Behavior behavior, Scalar type, String value) {
        public Mock {
            Objects.requireNonNull(behavior);
            if (capability == null || capability.isBlank() || capability.length() > 128)
                throw new IllegalArgumentException("Invalid mock capability");
            if (behavior == Behavior.RETURN) {
                if (type == null || value == null || value.length() > 8192)
                    throw new IllegalArgumentException("Invalid mock value");
            } else if (type != null || value != null)
                throw new IllegalArgumentException("Unexpected mock value");
        }

        @Override
        public String toString() {
            return "Mock[behavior=" + behavior + ", type=" + type + "]";
        }
    }
}
