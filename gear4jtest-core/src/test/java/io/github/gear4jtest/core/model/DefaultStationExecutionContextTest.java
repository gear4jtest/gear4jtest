package io.github.gear4jtest.core.model;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStationExecutionContextTest {
    @Test
    void addCapability_shouldExposeTypedCapability() {
        // Given
        DefaultStationExecutionContext context = newContext("station-1");

        // When
        context.addCapability(String.class, "value");

        // Then
        assertThat(context.getCapability(String.class)).contains("value");
        assertThat(context.getCapability(Integer.class)).isEmpty();
    }

    @Test
    void getResolvedParameters_shouldCreateAndReuseStationScopedCache() {
        // Given
        DefaultStationExecutionContext context = newContext("station-1");

        // When
        ResolvedParameters first = context.getResolvedParameters();
        ResolvedParameters second = context.getResolvedParameters();

        // Then
        assertThat(first).isSameAs(second);
        assertThat(context.getCapability(ResolvedParameters.class)).contains(first);
    }

    private static DefaultStationExecutionContext newContext(String operationId) {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .pipelineId("pipeline-1")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipeline-1", Map.of()))
                .build();
        StationLogTrace stationLog = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.PROCESSING, globalContext, stationLog, null);
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
