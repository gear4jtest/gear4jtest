package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ParameterResolutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkStationStrategyParameterCleanupTest {
    @Test
    void run_shouldCleanupInjectedParametersWhenOperatorFails() {
        // Given
        FailingOperator operator = new FailingOperator();
        WorkStation<String, String> station = new WorkStation.Builder<String, String, FailingOperator>()
                .id("failing")
                .type(FailingOperator.class)
                .parameter(FailingOperator::parameter, "runtime")
                .build();

        // When
        assertThatThrownBy(() -> strategy().run(station, "input", context("failing", operator), noopRunner()))
                .isInstanceOf(StationExecutionException.class)
                .hasRootCauseMessage("operator failed");

        // Then
        assertThat(operator.parameter().getValue()).isEqualTo("default");
    }

    @Test
    void run_shouldCleanupInjectedParametersWhenPostProcessorSkipsStation() {
        // Given
        EchoOperator operator = new EchoOperator();
        WorkStation.Builder<String, String, EchoOperator> builder = new WorkStation.Builder<String, String, EchoOperator>()
                .id("skipped")
                .type(EchoOperator.class)
                .parameter(EchoOperator::parameter, "runtime");
        builder.skipIfPost((input, ctx) -> true);
        WorkStation<String, String> station = builder.build();
        StationExecutionContext context = context("skipped", operator);

        // When
        StationLogTrace result = strategy().run(station, "input", context, noopRunner());

        // Then
        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(operator.parameter().getValue()).isEqualTo("default");
    }

    @Test
    void run_shouldCleanupAlreadyInjectedParametersWhenLaterResolutionFails() {
        // Given
        TwoParameterOperator operator = new TwoParameterOperator();
        Function<ParameterResolutionContext<String>, String> failingResolver = ctx -> {
            throw new IllegalStateException("resolution failed");
        };
        WorkStation<String, String> station = new WorkStation.Builder<String, String, TwoParameterOperator>()
                .id("partial-injection")
                .type(TwoParameterOperator.class)
                .parameter(TwoParameterOperator::firstParameter, "runtime")
                .parameter(TwoParameterOperator::secondParameter, failingResolver)
                .build();

        // When
        assertThatThrownBy(() -> strategy()
                .run(station, "input", context("partial-injection", operator), noopRunner()))
                .isInstanceOf(StationExecutionException.class)
                .hasRootCauseMessage("resolution failed");

        // Then
        assertThat(operator.firstParameter().getValue()).isEqualTo("first-default");
        assertThat(operator.secondParameter().getValue()).isEqualTo("second-default");
    }

    private static WorkStationStrategy strategy() {
        return WorkStationStrategy.builder().concurrencyManager(new WorkerConcurrencyManager()).build();
    }

    private static StationRunner noopRunner() {
        return (input, station, ctx) -> ctx.getRecord();
    }

    private static StationExecutionContext context(String operationId, Operator<?, ?> operator) {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new SingleResourceFactory(operator)))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
        StationLogTrace trace = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.PROCESSING, globalContext, trace, null);
    }

    private static StationParameter<String> perExecutionParameter(String defaultValue) {
        return StationParameter.<String>newBuilder()
                .defaultValue(defaultValue)
                .lifecyclePolicy(StationParameter.LifecyclePolicy.PER_EXECUTION)
                .build();
    }

    private static class EchoOperator implements Operator<String, String> {
        private final StationParameter<String> parameter = perExecutionParameter("default");

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return parameter.getValue();
        }

        StationParameter<String> parameter() {
            return parameter;
        }
    }

    private static final class FailingOperator extends EchoOperator {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            throw new IllegalStateException("operator failed");
        }
    }

    private static final class TwoParameterOperator implements Operator<String, String> {
        private final StationParameter<String> firstParameter = perExecutionParameter("first-default");
        private final StationParameter<String> secondParameter = perExecutionParameter("second-default");

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }

        StationParameter<String> firstParameter() {
            return firstParameter;
        }

        StationParameter<String> secondParameter() {
            return secondParameter;
        }
    }

    private record SingleResourceFactory(Operator<?, ?> operator) implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> type) {
            return type.cast(operator);
        }
    }
}
