package io.github.gear4jtest.core.sidecompute;

import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(registry.get(id)).isSameAs(ctx);
    }

    @Test
    void removeShouldRemoveContext() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext ctx = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(ctx.getExecutionId()).thenReturn(id);

        registry.register(ctx);
        registry.remove(id);

        assertThat(registry.get(id)).isNull();
    }

    @Test
    void getShouldReturnNullForUnknownId() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        assertThat(registry.get(UUID.randomUUID())).isNull();
    }
}
