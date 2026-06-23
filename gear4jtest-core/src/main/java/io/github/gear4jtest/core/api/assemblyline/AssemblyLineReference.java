package io.github.gear4jtest.core.api.assemblyline;

import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Stable declarative reference to a pipeline id/version pair.
 */
public record AssemblyLineReference(String assemblyLineId, String version) {
    public AssemblyLineReference {
        if (assemblyLineId == null || assemblyLineId.isBlank()) {
            throw new IllegalArgumentException("assemblyLineId must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }

    public static AssemblyLineReference from(AssemblyLine<?, ?> pipeline) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        return new AssemblyLineReference(pipeline.getId(), pipeline.getVersion());
    }

    public String displayName() {
        return assemblyLineId + ":" + version;
    }
}
