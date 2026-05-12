package io.github.gear4jtest.core.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedParametersConcurrencyTest {
    @Test
    void resolve_shouldReportCacheHitWhenAnotherThreadWinsTheRace() throws Exception {
        ResolvedParameters resolvedParameters = new ResolvedParameters();
        CountDownLatch computeStarted = new CountDownLatch(1);
        CountDownLatch releaseCompute = new CountDownLatch(1);
        AtomicInteger computeInvocations = new AtomicInteger();

        WorkerParamsInjector.ParameterModel<Operator<Object, Object>, Integer> parameterModel = new WorkerParamsInjector.ParameterModel<>(
                operator -> null) {
            @Override
            public Integer getValue(WorkerParamsInjector.InterpretationContext<?> ctx) {
                computeInvocations.incrementAndGet();
                computeStarted.countDown();
                try {
                    assertThat(releaseCompute.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                }
                return 42;
            }
        };

        ExecutionContext executionContext = new ExecutionContext(UUID.randomUUID(), "pipe",
                new ExecutionServices(null, new NoOpResourceFactory()),
                new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()));
        DefaultStationExecutionContext stationExecutionContext = new DefaultStationExecutionContext("step-1",
                executionContext, new ExecutionSupport(null, new TaskFactory(), PayloadCloners.immutableAware()));
        WorkerParamsInjector.InterpretationContext<String> interpretationContext = new WorkerParamsInjector.InterpretationContext<>(
                "item", executionContext, stationExecutionContext);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Callable<ResolvedParameters.Resolution<Integer>> resolveCall = () -> resolvedParameters
                    .resolve(parameterModel, interpretationContext);

            Future<ResolvedParameters.Resolution<Integer>> first = executorService.submit(resolveCall);
            assertThat(computeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            Future<ResolvedParameters.Resolution<Integer>> second = executorService.submit(resolveCall);

            releaseCompute.countDown();

            List<ResolvedParameters.Resolution<Integer>> resolutions = List.of(first.get(2, TimeUnit.SECONDS),
                                                                               second.get(2, TimeUnit.SECONDS));

            assertThat(computeInvocations.get()).isEqualTo(1);
            assertThat(resolutions).extracting(ResolvedParameters.Resolution::value).containsOnly(42);
            assertThat(resolutions).extracting(ResolvedParameters.Resolution::cacheHit).containsExactlyInAnyOrder(false,
                                                                                                                  true);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
