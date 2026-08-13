package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeExtensionOrderingTest {
    @Test
    void runInterceptors_shouldUseLowerOrderAsOutermostWrapper() {
        // Given
        List<String> calls = new ArrayList<>();
        var low = new RecordingRunInterceptor("low", 0, calls);
        var high = new RecordingRunInterceptor("high", 100, calls);

        // When
        var result = engine(List.of(high, low)).execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(calls).containsExactly("low-before", "high-before", "high-after", "low-after");
    }

    @Test
    void stationWrappers_shouldUseLowerOrderAsOutermostWrapper() {
        // Given
        List<String> calls = new ArrayList<>();
        var low = new RecordingStationWrapper("low", 0, calls);
        var high = new RecordingStationWrapper("high", 100, calls);

        // When
        var result = engine(List.of(high, low)).execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(calls).containsExactly("low-before", "high-before", "high-after", "low-after");
    }

    @Test
    void executorWrappers_shouldUseLowerOrderAsOutermostWrapper() {
        // Given
        List<String> calls = new ArrayList<>();
        var low = new RecordingExecutorWrapper("low", 0, calls);
        var high = new RecordingExecutorWrapper("high", 100, calls);
        ResolvedExtensions extensions = new RuntimeExtensionResolver(List.of(high, low)).resolve(null, null);
        var support = AssemblyLineRunSupportFactory.create(extensions, new TaskFactory(), null);
        DirectExecutor rawExecutor = new DirectExecutor();

        // When
        ExecutorService executor = support.executorFor(rawExecutor, null);
        executor.execute(() -> calls.add("task"));

        // Then
        assertThat(calls).containsExactly("low-before", "high-before", "task", "high-after", "low-after");
        assertThat(rawExecutor.isShutdown()).as("wrappers must not take ownership of the supplied executor").isFalse();
    }

    @Test
    void lifecycleObservers_shouldOpenHighToLowAndCompleteLowToHigh() {
        // Given
        List<String> calls = new ArrayList<>();
        var low = new RecordingLifecycle("low", 0, calls);
        var high = new RecordingLifecycle("high", 100, calls);

        // When
        var result = engine(List.of(low, high)).execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(calls).containsExactly(
                                          "run-high-start", "run-low-start",
                                          "station-high-start", "station-low-start",
                                          "station-low-completed", "station-high-completed",
                                          "run-low-completed", "run-high-completed");
    }

    private static AssemblyLineEngine engine(List<? extends RuntimeExtension> extensions) {
        return AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.copyOf(extensions)))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("extension-ordering")
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

    private record RecordingRunInterceptor(String name, int order, List<String> calls)
            implements RunInterceptorExtension {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                        RunRequest<IN> request,
                                                        ExecutionContext ctx,
                                                        RunChain<IN, OUT> chain) {
            calls.add(name + "-before");
            try {
                return chain.proceed();
            } finally {
                calls.add(name + "-after");
            }
        }
    }

    private record RecordingStationWrapper(String name, int order, List<String> calls)
            implements StationWrapperExtension {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
            return (input, station, stationCtx) -> {
                if (!isObserved(station)) {
                    return delegate.run(input, station, stationCtx);
                }
                calls.add(name + "-before");
                try {
                    return delegate.run(input, station, stationCtx);
                } finally {
                    calls.add(name + "-after");
                }
            };
        }
    }

    private record RecordingExecutorWrapper(String name, int order, List<String> calls)
            implements ExecutorWrapperExtension {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx) {
            return new ExecutorView(delegate, name, calls);
        }
    }

    private record RecordingLifecycle(String name, int order, List<String> calls)
            implements RunLifecycleExtension, StationLifecycleExtension {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public LifecycleFailureMode failureMode() {
            return LifecycleFailureMode.BEST_EFFORT;
        }

        @Override
        public void onRunStarted(ExecutionContext ctx, io.github.gear4jtest.core.api.trace.RunTrace run) {
            calls.add("run-" + name + "-start");
        }

        @Override
        public void onRunCompleted(ExecutionContext ctx, io.github.gear4jtest.core.api.trace.RunTrace run) {
            calls.add("run-" + name + "-completed");
        }

        @Override
        public void onStationStarted(ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot) {
            if ("echo".equals(snapshot.operationId())) {
                calls.add("station-" + name + "-start");
            }
        }

        @Override
        public void onStationCompleted(ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogRecord snapshot) {
            if ("echo".equals(snapshot.operationId())) {
                calls.add("station-" + name + "-completed");
            }
        }
    }

    private static boolean isObserved(AbstractStation<?, ?> station) {
        return "echo".equals(station.getId());
    }

    private static final class ExecutorView extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final String name;
        private final List<String> calls;

        private ExecutorView(ExecutorService delegate, String name, List<String> calls) {
            this.delegate = delegate;
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            calls.add(name + "-before");
            try {
                delegate.execute(command);
            } finally {
                calls.add(name + "-after");
            }
        }
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
