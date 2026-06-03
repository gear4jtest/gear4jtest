package io.github.gear4jtest.core.api.pipeline;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Runtime target used when Java code already has the child pipeline instance.
 */
public record DirectPipelineTarget<IN, OUT>(AssemblyLine<IN, OUT> pipeline) implements PipelineTarget<IN, OUT> {
    public DirectPipelineTarget {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
    }

    @Override
    public PipelineReference declaredReference() {
        return PipelineReference.from(pipeline);
    }

    @Override
    public Optional<PipelineReference> getResolvedReference() {
        return Optional.of(PipelineReference.from(pipeline));
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedPipeline() {
        return Optional.of(pipeline);
    }
}
