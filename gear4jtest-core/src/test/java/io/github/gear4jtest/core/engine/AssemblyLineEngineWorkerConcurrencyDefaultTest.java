package io.github.gear4jtest.core.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.engine.support.ConcurrencyAwareTransformer;
import io.github.gear4jtest.core.engine.support.WorkerStatefulness;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineEngineWorkerConcurrencyDefaultTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void defaultWorkerConcurrency_shouldUseProcessWideLockAcrossIndependentEngines() throws Exception {
        // Given
        SharedStatefulOperator.reset();
        CountDownLatch start = new CountDownLatch(1);
        AssemblyLine<String, String> assemblyLine = ElementModelBuilders.<String>createAssemblyLine("default-lock")
                .then(processingOperation("stateful", SharedStatefulOperator.class).build())
                .build();
        AssemblyLineEngine firstEngine = productionDefaultEngine();
        AssemblyLineEngine secondEngine = productionDefaultEngine();

        // When
        CompletableFuture<ExecutionResult<String>> first = executeWhenStarted(firstEngine, assemblyLine, start);
        CompletableFuture<ExecutionResult<String>> second = executeWhenStarted(secondEngine, assemblyLine, start);
        start.countDown();

        ExecutionResult<String> firstResult = first.get(5, TimeUnit.SECONDS);
        ExecutionResult<String> secondResult = second.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(SharedStatefulOperator.invocations()).isEqualTo(2);
        assertThat(SharedStatefulOperator.maxConcurrentInvocations())
                .as("the default production path must protect the same stateful worker instance even across engines")
                .isEqualTo(1);
    }

    private CompletableFuture<ExecutionResult<String>> executeWhenStarted(AssemblyLineEngine engine,
                                                                          AssemblyLine<String, String> assemblyLine,
                                                                          CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return engine.execute(assemblyLine, RunRequest.builder().input("input").build());
        }, executor);
    }

    private static AssemblyLineEngine productionDefaultEngine() {
        return AssemblyLineEngine.builder()
                .resourceFactory(new SharedResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    public static final class SharedResourceFactory implements ResourceFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getResource(Class<T> clazz) {
            if (clazz == SharedStatefulOperator.class) {
                return (T) SharedStatefulOperator.INSTANCE;
            }
            throw new IllegalArgumentException("Unsupported resource type: " + clazz.getName());
        }
    }

    public static final class SharedStatefulOperator
            implements Operator<String, String>, ConcurrencyAwareTransformer {
        private static final SharedStatefulOperator INSTANCE = new SharedStatefulOperator();
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();
        private static final AtomicInteger CURRENT = new AtomicInteger();
        private static final AtomicInteger MAX_CONCURRENT = new AtomicInteger();

        private SharedStatefulOperator() {
        }

        private static void reset() {
            INVOCATIONS.set(0);
            CURRENT.set(0);
            MAX_CONCURRENT.set(0);
        }

        private static int invocations() {
            return INVOCATIONS.get();
        }

        private static int maxConcurrentInvocations() {
            return MAX_CONCURRENT.get();
        }

        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATEFUL;
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            int concurrent = CURRENT.incrementAndGet();
            INVOCATIONS.incrementAndGet();
            MAX_CONCURRENT.accumulateAndGet(concurrent, Math::max);
            try {
                Thread.sleep(100);
                return input + "-done";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                CURRENT.decrementAndGet();
            }
        }
    }
}
