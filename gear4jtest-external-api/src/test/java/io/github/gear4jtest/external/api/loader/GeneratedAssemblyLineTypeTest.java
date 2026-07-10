package io.github.gear4jtest.external.api.loader;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeneratedAssemblyLineTypeTest {
    @Test
    void definition_shouldPreserveInputAndOutputTypes() {
        // Given
        @SuppressWarnings("unchecked")
        AssemblyLine<String, Integer> definition = mock(AssemblyLine.class);
        GeneratedAssemblyLine<String, Integer> generated = () -> definition;

        // When
        AssemblyLine<String, Integer> resolved = generated.getAssemblyLineDefinition();

        // Then
        assertThat(resolved).isSameAs(definition);
    }
}
