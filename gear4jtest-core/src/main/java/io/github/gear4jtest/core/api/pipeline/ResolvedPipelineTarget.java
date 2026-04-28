package io.github.gear4jtest.core.api.pipeline;

import io.github.gear4jtest.core.api.AssemblyLine;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime target produced after a declarative reference has been resolved by a compiler/loader.
 */
public record ResolvedPipelineTarget<IN, OUT>(
        PipelineReference declaredReference,
        PipelineReference resolvedReference,
        AssemblyLine<IN, OUT> pipeline)
        implements PipelineTarget<IN, OUT> {

    public ResolvedPipelineTarget {
        Objects.requireNonNull(declaredReference, "declaredReference must not be null");
        Objects.requireNonNull(resolvedReference, "resolvedReference must not be null");
        Objects.requireNonNull(pipeline, "pipeline must not be null");
    }

    @Override
    public Optional<PipelineReference> getResolvedReference() {
        return Optional.of(resolvedReference);
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedPipeline() {
        return Optional.of(pipeline);
    }
}
