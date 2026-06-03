package io.github.gear4jtest.core.model;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationContextUtilsTest {
    @Test
    void getTransformer_shouldReturnEmptyWhenNoOperatorIsBound() {
        // Given
        DefaultStationExecutionContext context = newContext("station-1");

        // When / Then
        assertThat(StationContextUtils.getTransformer(context)).isEmpty();
        assertThat(StationContextUtils.applyTransformer("input", context)).isEmpty();
    }

    @Test
    void applyTransformer_shouldInvokeBoundOperator() {
        // Given
        DefaultStationExecutionContext context = newContext("station-1");
        Operator<String, String> operator = (input, ctx) -> input.toUpperCase();
        context.addCapability(Operator.class, operator);

        // When / Then
        assertThat(StationContextUtils.getTransformer(context)).contains(operator);
        assertThat(StationContextUtils.applyTransformer("gear4j", context)).contains("GEAR4J");
    }

    @Test
    void getProcessingParameters_shouldReturnBoundParametersCapability() {
        // Given
        DefaultStationExecutionContext context = newContext("station-1");
        WorkerParamsInjector.Parameters parameters = new WorkerParamsInjector.Parameters();

        // When
        context.addCapability(WorkerParamsInjector.Parameters.class, parameters);

        // Then
        assertThat(StationContextUtils.getProcessingParameters(context)).contains(parameters);
    }

    private static DefaultStationExecutionContext newContext(String operationId) {
        ExecutionContext globalContext = new ExecutionContext(UUID.randomUUID(), "pipeline-1",
                new ExecutionServices(null, noResources()),
                new AssemblyRunTrace(UUID.randomUUID(), "pipeline-1", Map.of()));
        StationLogTrace record = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.PROCESSING, globalContext, record, null);
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
