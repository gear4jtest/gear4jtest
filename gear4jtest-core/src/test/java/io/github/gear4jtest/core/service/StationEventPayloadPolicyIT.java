package io.github.gear4jtest.core.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class StationEventPayloadPolicyIT {

    @Test
    void stationEvents_shouldRespectConfiguredPayloadPolicy() {
        CopyOnWriteArrayList<StationFinishedEvent> seenEvents = new CopyOnWriteArrayList<>();

        AssemblyLine<String, Integer> pipeline = ElementModelBuilders.<String>createAssemblyLine("payload-policy")
                .configuration(AssemblyLine.Configuration.builder()
                        .eventHandling(EventHandlingDefinition.builder()
                                .subscription(EventSubscription.on(StationFinishedEvent.class, seenEvents::add))
                                .globalEventConfiguration(EventHandlingDefinition.EventConfiguration.builder()
                                        .eventPayloadPolicy(EventPayloadPolicy.discard())
                                        .build())
                                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                        .shutdownTimeout(Duration.ofSeconds(2))
                                        .build())
                                .build())
                        .build())
                .then(ElementModelBuilders.<String, Integer, LengthOperator>processingOperation("step-1", LengthOperator.class)
                        .build())
                .build();

        PipelineEngine engine = PipelineEngine.builder()
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .resourceFactory(new SingleResourceFactory(new LengthOperator()))
                .extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();

        ExecutionResult<Integer> result = engine.execute(pipeline, RunRequest.builder().input("abcd").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(seenEvents).hasSize(1);
        assertThat(seenEvents.get(0).getInput()).isNull();
        assertThat(seenEvents.get(0).getOutput()).isNull();
    }

    static final class LengthOperator implements Operator<String, Integer> {
        @Override
        public Integer transform(String input, StationExecutionContext operationExecution) {
            return input.length();
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
