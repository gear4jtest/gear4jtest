package io.github.gear4jtest.core.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.StationSignalType;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SignalStationFlowIT {
    private final InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();

    @BeforeEach
    void resetOperatorState() {
        RecordingOperator.invocations.set(0);
    }

    @Test
    void stopSignal_shouldInterruptSequenceAndPersistStoppedRunWithoutRunningNextStation() {
        // Given
        var pipeline = ElementModelBuilders.<String>createAssemblyLine("stop-signal")
                .then(new SignalStation.Builder<String>()
                        .id("stop-now")
                        .type(StationSignalType.STOP)
                        .condition(signal -> true)
                        .build())
                .then(processingOperation("after-stop", RecordingOperator.class).build())
                .build();
        AssemblyLineEngine engine = engine();

        // When
        ExecutionResult<String> result = engine.execute(pipeline, RunRequest.builder().input("payload").build());

        // Then
        assertThat(result.isStopped()).isTrue();
        assertThat(result.getResult()).isEqualTo("payload");
        assertThat(RecordingOperator.invocations).hasValue(0);
        assertThat(repository.findById(result.getExecution().getId())).get()
                .extracting(run -> run.status(), run -> run.result())
                .containsExactly(ExecutionStatus.STOPPED, "payload");
        assertThat(repository.findAllLogsByRunId(result.getExecution().getId(), PageRequest.first(10)))
                .extracting(StationLogRecord::operationId, StationLogRecord::status)
                .contains(tuple("stop-signal:root", StationLogStatus.STOPPED),
                          tuple("stop-now", StationLogStatus.STOPPED))
                .doesNotContain(tuple("after-stop", StationLogStatus.SUCCEEDED));
    }

    private AssemblyLineEngine engine() {
        return AssemblyLineEngine.builder()
                .resourceFactory(new ReflectiveResourceFactory())
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .extensionResolver(new RuntimeExtensionResolver(List.of(new PersistenceExtension(
                        InMemoryExecutionManager.builder().repository(repository).build()))))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    public static final class RecordingOperator implements Operator<String, String> {
        static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            invocations.incrementAndGet();
            return input + "-unexpected";
        }
    }

    private static final class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
