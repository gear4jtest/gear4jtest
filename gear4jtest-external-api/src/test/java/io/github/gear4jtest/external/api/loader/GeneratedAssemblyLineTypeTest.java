package io.github.gear4jtest.external.api.loader;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedAssemblyLineTypeTest {
    @Test
    void definition_shouldPreserveInputAndOutputTypes() {
        // Given
        AssemblyLine<String, Integer> definition = AssemblyLine.<String, Integer>builder("typed").build();
        GeneratedAssemblyLine<String, Integer> generated = () -> definition;

        // When
        AssemblyLine<String, Integer> resolved = generated.getAssemblyLineDefinition();

        // Then
        assertThat(resolved).isSameAs(definition);
    }
}
