package io.github.gear4jtest.core.api.assemblyline;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AssemblyLineTargetTest {
    @Test
    void referencedTarget_shouldExposeOnlyDeclaredReference() {
        AssemblyLineReference reference = new AssemblyLineReference("pipeline", "1");
        ReferencedAssemblyLineTarget<String, String> target = new ReferencedAssemblyLineTarget<>(reference);

        assertThat(target.declaredReference()).isSameAs(reference);
        assertThat(target.getResolvedReference()).isEmpty();
        assertThat(target.getResolvedAssemblyLine()).isEmpty();
    }

    @Test
    void resolvedTarget_shouldExposeDeclaredResolvedReferenceAndAssemblyLine() {
        AssemblyLineReference declaredReference = new AssemblyLineReference("pipeline", "latest");
        AssemblyLineReference resolvedReference = new AssemblyLineReference("pipeline", "1");
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipeline").version("1").build();

        ResolvedAssemblyLineTarget<String, String> target = new ResolvedAssemblyLineTarget<>(declaredReference,
                resolvedReference, pipeline);

        assertThat(target.declaredReference()).isSameAs(declaredReference);
        assertThat(target.getResolvedReference()).hasValue(resolvedReference);
        assertThat(target.getResolvedAssemblyLine())
                .hasValueSatisfying(resolved -> assertThat(resolved).isSameAs(pipeline));
    }

    @Test
    void targets_shouldRejectNullMandatoryArguments() {
        AssemblyLineReference reference = new AssemblyLineReference("pipeline", "1");
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipeline").build();

        assertThatNullPointerException().isThrownBy(() -> new ReferencedAssemblyLineTarget<>(null));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedAssemblyLineTarget<>(null, reference, pipeline));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedAssemblyLineTarget<>(reference, null, pipeline));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedAssemblyLineTarget<>(reference, reference, null));
    }
}
