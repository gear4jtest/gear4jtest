package io.github.gear4jtest.core.engine.runner;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class SyntheticStationLifecycleRecorderTest {
    @Test
    void payloadMappingFailure_shouldNotEscapeSyntheticLifecyclePaths() {
        // Given
        SyntheticStationLifecycleRecorder recorder = new SyntheticStationLifecycleRecorder(List.of());
        StationExecutionContext parentContext = parentContextWithFailingPayloadPolicy();
        WorkStation<String, String> station = new WorkStation.Builder<String, String, EchoOperator>()
                .id("synthetic")
                .type(EchoOperator.class)
                .build();
        RuntimeException failure = new IllegalStateException("station failed");

        // When / Then
        assertThatCode(() -> recorder.recordSkipped(parentContext, station, trace("skipped"), "input",
                                                    StationSkipReason.CONDITION_NOT_SATISFIED))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.recordCancelled(parentContext, station, trace("cancelled"), "input",
                                                      StationCancellationReason.TIMEOUT, failure))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.recordInterrupted(parentContext, station, trace("interrupted"), "input",
                                                        StationInterruptionReason.SIBLING_FLOW_INTERRUPTED,
                                                        "sibling", failure))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.recordFailedBeforeStart(parentContext, station, trace("failed"), "input",
                                                              failure))
                .doesNotThrowAnyException();
    }

    private static StationExecutionContext parentContextWithFailingPayloadPolicy() {
        EventPayloadPolicy policy = new EventPayloadPolicy() {
            @Override
            public Object mapStationInput(Object input, StationExecutionContext stationExecutionContext) {
                throw new IllegalStateException("payload mapping failed");
            }
        };
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .globalEventConfiguration(EventHandlingDefinition.EventConfiguration.builder()
                        .eventPayloadPolicy(policy)
                        .build())
                .build();
        ExecutionContext executionContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(mock(EventManager.class), new NoOpResourceFactory()))
                .eventRuntimeOptions(ExecutionContext.EventRuntimeOptions.from(definition))
                .build();
        return new DefaultStationExecutionContext("parent", StationKind.CONTAINER, executionContext, trace("parent"),
                new ExecutionSupport(null, null, null));
    }

    private static StationLogTrace trace(String operationId) {
        return StationLogTrace.start(UUID.randomUUID(), operationId, null);
    }

    private static final class EchoOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> type) {
            return null;
        }
    }
}
