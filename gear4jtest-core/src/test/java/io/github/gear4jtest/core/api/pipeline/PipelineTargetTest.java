package io.github.gear4jtest.core.api.pipeline;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PipelineTargetTest {
    @Test
    void referencedTarget_shouldExposeOnlyDeclaredReference() {
        PipelineReference reference = new PipelineReference("pipeline", "1");
        ReferencedPipelineTarget<String, String> target = new ReferencedPipelineTarget<>(reference);

        assertThat(target.declaredReference()).isSameAs(reference);
        assertThat(target.getResolvedReference()).isEmpty();
        assertThat(target.getResolvedPipeline()).isEmpty();
    }

    @Test
    void resolvedTarget_shouldExposeDeclaredResolvedReferenceAndPipeline() {
        PipelineReference declaredReference = new PipelineReference("pipeline", "latest");
        PipelineReference resolvedReference = new PipelineReference("pipeline", "1");
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipeline").version("1").build();

        ResolvedPipelineTarget<String, String> target = new ResolvedPipelineTarget<>(declaredReference,
                resolvedReference, pipeline);

        assertThat(target.declaredReference()).isSameAs(declaredReference);
        assertThat(target.getResolvedReference()).hasValue(resolvedReference);
        assertThat(target.getResolvedPipeline())
                .hasValueSatisfying(resolved -> assertThat(resolved).isSameAs(pipeline));
    }

    @Test
    void targets_shouldRejectNullMandatoryArguments() {
        PipelineReference reference = new PipelineReference("pipeline", "1");
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipeline").build();

        assertThatNullPointerException().isThrownBy(() -> new ReferencedPipelineTarget<>(null));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedPipelineTarget<>(null, reference, pipeline));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedPipelineTarget<>(reference, null, pipeline));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedPipelineTarget<>(reference, reference, null));
    }
}
