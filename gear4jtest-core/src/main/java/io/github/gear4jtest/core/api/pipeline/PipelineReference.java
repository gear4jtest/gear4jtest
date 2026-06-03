package io.github.gear4jtest.core.api.pipeline;

import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Stable declarative reference to a pipeline id/version pair.
 */
public record PipelineReference(String pipelineId, String version) {
    public PipelineReference {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }

    public static PipelineReference from(AssemblyLine<?, ?> pipeline) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        return new PipelineReference(pipeline.getId(), pipeline.getVersion());
    }

    public String displayName() {
        return pipelineId + ":" + version;
    }
}
