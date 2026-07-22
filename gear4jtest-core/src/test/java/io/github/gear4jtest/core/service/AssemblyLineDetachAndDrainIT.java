package io.github.gear4jtest.core.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class AssemblyLineDetachAndDrainIT {
    private static void awaitRegistryRemoval(ExecutionContextRegistry registry, UUID executionId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (registry.find(executionId) == null) {
                return;
            }
            Thread.sleep(25L);
        }
    }

    @Test
    void execute_shouldKeepExecutionContextRegisteredUntilDetachedDrainCompletes() throws Exception {
        CountDownLatch reactionStarted = new CountDownLatch(1);
        CountDownLatch releaseReaction = new CountDownLatch(1);
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        AssemblyLine<String, Integer> pipeline = AssemblyLines.<String>createAssemblyLine("detach-drain")
                .configuration(AssemblyLine.Configuration.builder().eventHandling(EventHandlingDefinition.builder()
                        .subscription(EventSubscription.on(StationFinishedEvent.class, event -> {
                            reactionStarted.countDown();
                            assertThat(releaseReaction.await(2, TimeUnit.SECONDS)).isTrue();
                        }))
                        .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                .shutdownTimeout(Duration.ofSeconds(2))
                                .shutdownMode(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN)
                                .build())
                        .build()).build())
                .then(Stations.<String, Integer, LengthOperator>processingOperation("step-1", LengthOperator.class)
                        .build())
                .build();

        AssemblyLineEngine engine = AssemblyLineEngine.builder()
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .resourceFactory(new SingleResourceFactory(new LengthOperator()))
                .extensionResolver(new RuntimeExtensionResolver(List.of())).executionContextRegistry(registry).build();

        ExecutionResult<Integer> result = engine.execute(pipeline, RunRequest.builder().input("abcd").build());
        UUID executionId = result.getExecution().getId();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo(4);
        assertThat(reactionStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.find(executionId)).isNotNull();

        releaseReaction.countDown();
        awaitRegistryRemoval(registry, executionId);
        assertThat(registry.find(executionId)).isNull();
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
