package io.github.gear4jtest.core.sidecompute;

import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionContextRegistryTest {
    @Test
    void registerAndGet_shouldReturnSameContext() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext ctx = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(ctx.getExecutionId()).thenReturn(id);

        registry.register(ctx);

        assertThat(registry.find(id)).isSameAs(ctx);
    }

    @Test
    void removeShouldRemoveContext() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext ctx = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(ctx.getExecutionId()).thenReturn(id);

        registry.register(ctx);
        registry.remove(id);

        assertThat(registry.find(id)).isNull();
    }

    @Test
    void getShouldReturnNullForUnknownId() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        assertThat(registry.find(UUID.randomUUID())).isNull();
    }

    @Test
    void registerShouldRejectDuplicateActiveExecutionId() {
        // Given
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        UUID id = UUID.randomUUID();
        ExecutionContext first = mock(ExecutionContext.class);
        ExecutionContext duplicate = mock(ExecutionContext.class);
        when(first.getExecutionId()).thenReturn(id);
        when(duplicate.getExecutionId()).thenReturn(id);
        registry.register(first);

        // When / Then
        assertThatThrownBy(() -> registry.register(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate active execution id: " + id);
        assertThat(registry.find(id)).isSameAs(first);
    }

    @Test
    void conditionalRemoveShouldNotRemoveReplacementContext() {
        // Given
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        UUID id = UUID.randomUUID();
        ExecutionContext completed = mock(ExecutionContext.class);
        ExecutionContext replacement = mock(ExecutionContext.class);
        when(completed.getExecutionId()).thenReturn(id);
        when(replacement.getExecutionId()).thenReturn(id);
        registry.register(completed);
        registry.remove(id, completed);
        registry.register(replacement);

        // When
        registry.remove(id, completed);

        // Then
        assertThat(registry.find(id)).isSameAs(replacement);
    }

    @Test
    void conditionalRemoveShouldRemoveExpectedContext() {
        // Given
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext context = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(context.getExecutionId()).thenReturn(id);
        registry.register(context);

        // When
        registry.remove(id, context);

        // Then
        assertThat(registry.find(id)).isNull();
    }
}
