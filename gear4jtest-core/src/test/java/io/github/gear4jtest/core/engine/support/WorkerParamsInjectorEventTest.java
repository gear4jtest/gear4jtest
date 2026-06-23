package io.github.gear4jtest.core.engine.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.event.ParameterResolvedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerParamsInjectorEventTest {
    @Test
    void should_publish_parameter_resolved_event_when_enabled() {
        CopyOnWriteArrayList<ParameterResolvedEvent> seenEvents = new CopyOnWriteArrayList<>();

        AssemblyLine<String, Integer> pipeline = ElementModelBuilders.<String>createAssemblyLine("param-events")
                .configuration(AssemblyLine.Configuration.builder()
                        .eventHandling(EventHandlingDefinition.builder()
                                .subscription(io.github.gear4jtest.core.event.EventSubscription
                                        .on(ParameterResolvedEvent.class, seenEvents::add))
                                .globalEventConfiguration(EventHandlingDefinition.EventConfiguration.builder()
                                        .eventOnParameterChanged(true).build())
                                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                        .shutdownTimeout(Duration.ofSeconds(2)).build())
                                .build())
                        .build())
                .then(ElementModelBuilders
                        .<String, Integer, ParamEchoOperator>processingOperation("step-1", ParamEchoOperator.class)
                        .parameter(ParamEchoOperator::getLengthParam,
                                   (Function<WorkerParamsInjector.InterpretationContext<String>, Integer>) ctx -> ctx
                                           .getItem().length())
                        .build())
                .build();

        AssemblyLineEngine engine = AssemblyLineEngine.builder()
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .resourceFactory(new SingleResourceFactory(new ParamEchoOperator()))
                .extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(new ExecutionContextRegistry()).build();

        ExecutionResult<Integer> result = engine.execute(pipeline, RunRequest.builder().input("abcd").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo(4);
        assertThat(seenEvents).hasSize(1);
        assertThat(seenEvents.get(0).getOperationId()).isEqualTo("step-1");
        assertThat(seenEvents.get(0).isCacheHit()).isFalse();
        assertThat(seenEvents.get(0).getValueType()).isEqualTo(Integer.class.getName());
    }

    static final class ParamEchoOperator implements Operator<String, Integer> {
        private final WorkerParamsInjector.Parameter<Integer> lengthParam = WorkerParamsInjector.Parameter
                .<Integer>newBuilder().build();

        @Override
        public Integer transform(String input, StationExecutionContext operationExecution) {
            return lengthParam.getValue();
        }

        WorkerParamsInjector.Parameter<Integer> getLengthParam() {
            return lengthParam;
        }
    }

    static final class SingleResourceFactory implements ResourceFactory {
        private final Object instance;

        SingleResourceFactory(Object instance) {
            this.instance = instance;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getResource(Class<T> clazz) {
            return (T) instance;
        }
    }
}
