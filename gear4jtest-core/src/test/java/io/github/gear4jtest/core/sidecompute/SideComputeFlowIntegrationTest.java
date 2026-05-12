package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SideComputeFlowIntegrationTest {

    @Test
    void failing_required_processor_should_abort_station_before_operator_execution() {
        AtomicInteger operatorExecutions = new AtomicInteger();

        AssemblyLine<String, String> pipeline = ElementModelBuilders.<String>createAssemblyLine("required-processor")
                .configuration(AssemblyLine.Configuration.builder()
                        .eventHandling(EventHandlingDefinition.builder()
                                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                        .shutdownTimeout(Duration.ofSeconds(2)).build())
                                .build())
                        .build())
                .then(ElementModelBuilders
                        .<String, String, CountingOperator>processingOperation("step-1", CountingOperator.class)
                        .addProcessor(SideComputeWaitProcessor.builder("missing-key").timeout(Duration.ofMillis(50))
                                .onTimeoutFail().build())
                        .build())
                .build();

        PipelineEngine engine = PipelineEngine.builder()
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .resourceFactory(new CountingResourceFactory(new CountingOperator(operatorExecutions)))
                .extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(new ExecutionContextRegistry()).build();

        ExecutionResult<String> result = engine.execute(pipeline, RunRequest.builder().input("hello").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isNotNull();
        assertThat(result.getExecution().getErrorMessage()).contains("missing-key");
        assertThat(operatorExecutions).hasValue(0);
    }

    static final class CountingOperator implements Operator<String, String> {
        private final AtomicInteger executions;

        CountingOperator(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            executions.incrementAndGet();
            return input.toUpperCase();
        }
    }

    static final class CountingResourceFactory implements ResourceFactory {
        private final CountingOperator operator;

        CountingResourceFactory(CountingOperator operator) {
            this.operator = operator;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getResource(Class<T> clazz) {
            return (T) operator;
        }
    }
}
