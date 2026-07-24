package io.github.gear4jtest.jdbc.migration;

import java.time.Instant;
import java.util.Objects;

/** Immutable diagnostic view of one Gear4J-managed schema migration. */
public record SchemaMigrationStatus(String moduleId,
                                    String version,
                                    String description,
                                    String checksum,
                                    SchemaMigrationState state,
                                    Instant recordedAt) {
    public SchemaMigrationStatus {
        moduleId = requireNonBlank(moduleId, "moduleId");
        version = requireNonBlank(version, "version");
        description = Objects.requireNonNull(description, "description must not be null");
        checksum = requireNonBlank(checksum, "checksum");
        state = Objects.requireNonNull(state, "state must not be null");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
