package io.github.gear4jtest.core.api.pipeline;

import io.github.gear4jtest.core.api.AssemblyLine;
import java.util.Objects;
import java.util.Optional;

/**
 * Declarative target used before a compiler/loader resolves the referenced pipeline.
 */
public record ReferencedPipelineTarget<IN, OUT>(PipelineReference reference)
        implements PipelineTarget<IN, OUT> {

    public ReferencedPipelineTarget {
        Objects.requireNonNull(reference, "reference must not be null");
    }

    @Override
    public PipelineReference declaredReference() {
        return reference;
    }

    @Override
    public Optional<PipelineReference> getResolvedReference() {
        return Optional.empty();
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedPipeline() {
        return Optional.empty();
    }
}
