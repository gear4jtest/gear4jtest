package io.github.gear4jtest.micrometer;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.AssemblyLineExecutors;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Gear4jMicrometerLifecycleOrderingTest {
    @Test
    void runMetrics_shouldObserveFailureFromEarlierCriticalCompletionObserver() {
        // Given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AssemblyLineExecutor executor = executor(new CriticalRunCompletionExtension(),
                                                 new Gear4jMicrometerExtension(registry));

        // When
        var result = executor.execute(pipeline(), RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(registry.counter("gear4j.runs.started").count()).isEqualTo(1.0d);
        assertThat(registry.counter("gear4j.runs.completed", "status", "FAILED").count()).isEqualTo(1.0d);
        assertThat(registry.find("gear4j.runs.completed").tag("status", "SUCCEEDED").counter()).isNull();
    }

    @Test
    void stationMetrics_shouldObserveFailureFromEarlierCriticalCompletionObserver() {
        // Given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AssemblyLineExecutor executor = executor(new CriticalEchoCompletionExtension(),
                                                 new Gear4jMicrometerExtension(registry));

        // When
        var result = executor.execute(pipeline(), RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(registry.counter("gear4j.stations.completed", "status", "FAILED").count())
                .as("the terminal observer must see the normalized critical lifecycle failure")
                .isEqualTo(1.0d);
        assertThat(registry.find("gear4j.stations.completed").tag("status", "SUCCEEDED").counter()).isNull();
    }

    private static AssemblyLineExecutor executor(RuntimeExtension first, RuntimeExtension second) {
        return AssemblyLineExecutors.builder()
                .resourceFactory(reflectiveResourceFactory())
                .runtimeExtensions(first, second)
                .build();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("micrometer-lifecycle-order")
                .then(Stations.processingOperation("echo", EchoOperator.class).build())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    public static final class EchoOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    private static final class CriticalRunCompletionExtension implements RunLifecycleExtension {
        @Override
        public LifecycleFailureMode failureMode() {
            return LifecycleFailureMode.CRITICAL;
        }

        @Override
        public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
            throw new IllegalStateException("critical run completion failure");
        }
    }

    private static final class CriticalEchoCompletionExtension implements StationLifecycleExtension {
        @Override
        public LifecycleFailureMode failureMode() {
            return LifecycleFailureMode.CRITICAL;
        }

        @Override
        public void onStationCompleted(ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogRecord snapshot) {
            if ("echo".equals(snapshot.operationId())) {
                throw new IllegalStateException("critical station completion failure");
            }
        }
    }
}
