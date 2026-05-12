package io.github.gear4jtest.core.api.pipeline;

import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Target of a pipeline-call station.
 *
 * <p>
 * Declarative models may initially carry only a
 * {@link ReferencedPipelineTarget}. Runtime-ready models should use either
 * {@link DirectPipelineTarget} or {@link ResolvedPipelineTarget} so execution
 * does not perform loading/resolution work.
 * </p>
 */
public sealed interface PipelineTarget<IN, OUT>
        permits DirectPipelineTarget, ReferencedPipelineTarget, ResolvedPipelineTarget {

    PipelineReference declaredReference();

    Optional<PipelineReference> getResolvedReference();

    Optional<AssemblyLine<IN, OUT>> getResolvedPipeline();
}
