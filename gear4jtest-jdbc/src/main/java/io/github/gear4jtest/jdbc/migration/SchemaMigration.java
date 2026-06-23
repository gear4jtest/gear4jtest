package io.github.gear4jtest.jdbc.migration;

import java.util.Objects;

/** A versioned SQL migration loaded from the classpath. */
public record SchemaMigration(String version, String description, String resourcePath) {
    public SchemaMigration {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
    }
}
