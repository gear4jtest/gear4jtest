package io.github.gear4jtest.core.engine;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineRunCleanupTest {
    @Test
    void cleanupShouldNotRemoveAReplacementContextWithTheSameId() {
        // Given
        UUID executionId = UUID.randomUUID();
        ExecutionContext completed = executionContext(executionId);
        ExecutionContext replacement = executionContext(executionId);
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        registry.register(completed);
        registry.remove(executionId, completed);
        registry.register(replacement);

        // When
        AssemblyLineRunCleanup.cleanup(completed, registry).run();

        // Then
        assertThat(registry.get(executionId)).isSameAs(replacement);
    }

    private static ExecutionContext executionContext(UUID executionId) {
        AssemblyRunTrace run = new AssemblyRunTrace(executionId, "pipeline", Map.of());
        return ExecutionContext.builder()
                .executionId(executionId)
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(run)
                .build();
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }
}
