package io.github.gear4jtest.studio.model;

import java.util.Objects;

public record ParameterValue(Kind kind, String value) {
    public ParameterValue {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
    }

    public enum Kind {
        TEXT, RESOURCE_REFERENCE
    }
}
