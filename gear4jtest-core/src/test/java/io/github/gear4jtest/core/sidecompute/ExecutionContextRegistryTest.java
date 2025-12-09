package io.github.gear4jtest.core.sidecompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;

class ExecutionContextRegistryTest {

    @Test
    void registerAndGet_shouldReturnSameContext() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext ctx = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(ctx.getExecutionId()).thenReturn(id);

        registry.register(ctx);

        assertThat(registry.get(id.toString()))
                .isSameAs(ctx);
    }

    @Test
    void removeShouldRemoveContext() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext ctx = mock(ExecutionContext.class);
        UUID id = UUID.randomUUID();
        when(ctx.getExecutionId()).thenReturn(id);

        registry.register(ctx);
        registry.remove(id.toString());

        assertThat(registry.get(id.toString()))
                .isNull();
    }

    @Test
    void getShouldReturnNullForUnknownId() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        assertThat(registry.get("unknown"))
                .isNull();
    }
}
