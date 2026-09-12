package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class SideComputeFlowIntegrationTest {
    @ParameterizedTest
    @EnumSource(SideComputeWaitProcessor.OnTimeout.class)
    void cancellationDuringWait_shouldCancelWithoutFallbackOrOperatorInvocation(
                                                                                SideComputeWaitProcessor.OnTimeout timeoutMode)
            throws Exception {
        // Given
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        CancellationToken token = new CancellationToken();
        var waitBuilder = SideComputeWaitProcessor.builder("pending").timeout(Duration.ofSeconds(30));
        switch (timeoutMode) {
            case FAIL_ASSEMBLY_LINE -> waitBuilder.onTimeoutFail();
            case IGNORE -> waitBuilder.onTimeoutIgnore();
            case USE_FALLBACK -> waitBuilder.onTimeoutUseFallback(() -> {
                fallbackCalls.incrementAndGet();
                return "fallback";
            });
        }
        Processor enteredProcessor = new Processor() {
            @Override
            public <I> void beforeExecution(I input, StationExecutionContext context) {
                callerThread.set(Thread.currentThread());
                entered.countDown();
            }

            @Override
            public void afterExecution(Object result, StationExecutionContext context) {
            }
        };
        var pipeline = AssemblyLines.<String>createAssemblyLine("cancel-side-compute")
                .then(Stations.processingOperation("wait", CountingOperator.class)
                        .addProcessor(enteredProcessor).addProcessor(waitBuilder.build()).build())
                .build();
        var engine = AssemblyLineEngine.builder()
                .resourceFactory(new CountingResourceFactory(new CountingOperator(invocations)))
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
        var caller = Executors.newSingleThreadExecutor();
        try {
            var execution = caller.submit(() -> engine.execute(pipeline,
                                                               RunRequest.builder().input("input")
                                                                       .cancellationToken(token).build()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (callerThread.get().getState() != Thread.State.TIMED_WAITING && System.nanoTime() < waitDeadline) {
                Thread.sleep(1);
            }
            assertThat(callerThread.get().getState()).isEqualTo(Thread.State.TIMED_WAITING);

            // When
            token.cancel("cancel while waiting");
            var result = execution.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(result.isCancelled()).isTrue();
            assertThat(result.getExecution().getStatus())
                    .isEqualTo(io.github.gear4jtest.core.persistence.ExecutionStatus.CANCELLED);
            assertThat(result.getError()).hasCause(token.cancellationCause().orElseThrow());
            assertThat(invocations).hasValue(0);
            assertThat(fallbackCalls).hasValue(0);
            assertThat(caller.isShutdown()).isFalse();
        } finally {
            caller.shutdownNow();
            assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failing_required_processor_should_abort_station_before_operator_execution() {
        AtomicInteger operatorExecutions = new AtomicInteger();

        AssemblyLine<String, String> pipeline = AssemblyLines.<String>createAssemblyLine("required-processor")
                .configuration(AssemblyLine.Configuration.builder()
                        .eventHandling(EventHandlingDefinition.builder()
                                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                        .shutdownTimeout(Duration.ofSeconds(2)).build())
                                .build())
                        .build())
                .then(Stations.<String, String, CountingOperator>processingOperation("step-1", CountingOperator.class)
                        .addProcessor(SideComputeWaitProcessor.builder("missing-key").timeout(Duration.ofMillis(50))
                                .onTimeoutFail().build())
                        .build())
                .build();

        AssemblyLineEngine engine = AssemblyLineEngine.builder()
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
        public <T> T getResource(Class<T> clazz) {
            return clazz.cast(operator);
        }
    }
}
