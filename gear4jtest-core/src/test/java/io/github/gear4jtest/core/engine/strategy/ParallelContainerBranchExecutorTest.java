package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.EngineStationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static io.github.gear4jtest.core.api.config.FlowConfig.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

class ParallelContainerBranchExecutorTest {
    @Test
    void execute_shouldRecordRejectedBranchSubmissionsAsFailures() {
        // Given
        ExecutorService rejectedExecutor = Executors.newSingleThreadExecutor();
        rejectedExecutor.shutdown();
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(rejectedExecutor)
                .id("container")
                .withBranch("rejected", new TestStation("branch"))
                .build();
        TestStationExecutionContext context = stationContext("container");

        // When
        ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofMillis(100));

        // Then
        assertThat(aggregation.results()).hasSize(1);
        StationLogTrace branchLog = aggregation.results().get(0);
        assertThat(branchLog.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(branchLog.getBranchId()).isEqualTo("rejected");
        assertThat(branchLog.getParentOperationId()).isEqualTo(context.stationLogTrace().getId());
        assertThat(aggregation.interruptingChild()).isSameAs(branchLog);
    }

    @Test
    void execute_shouldCreateSkippedSyntheticLogsForBranchesWithFalseConditions() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                    .id("container")
                    .withBranch("skipped", new TestStation("branch"), (input, ctx) -> false)
                    .build();
            TestStationExecutionContext context = stationContext("container");

            // When
            ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                    .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofMillis(100));

            // Then
            assertThat(aggregation.results()).hasSize(1);
            StationLogTrace branchLog = aggregation.results().get(0);
            assertThat(branchLog.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
            assertThat(branchLog.getBranchId()).isEqualTo("skipped");
            assertThat(aggregation.interruptingChild()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_shouldPropagateMdcToParallelBranches() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                    .id("container")
                    .withBranch("branch-1", new TestStation("branch"))
                    .build();
            TestStationExecutionContext context = stationContext("container");
            AtomicReference<String> seenExecutionId = new AtomicReference<>();
            AtomicReference<String> seenAssemblyLineId = new AtomicReference<>();
            io.github.gear4jtest.core.spi.runner.StationRunner runner = (input, child, childContext) -> {
                seenExecutionId.set(MDC.get("gear4j.executionId"));
                seenAssemblyLineId.set(MDC.get("gear4j.assemblyLineId"));
                StationLogTrace log = StationLogTrace.start(childContext.getGlobalContext().getExecutionId(),
                                                            child.getId(), null);
                log.markSuccess(input);
                return log;
            };

            MDC.clear();
            try {
                MDC.put("gear4j.executionId", "run-123");
                MDC.put("gear4j.assemblyLineId", "pipeline-1");

                // When
                ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                        .execute(station, "input", runner, context, DEFAULT, Duration.ofSeconds(2));

                // Then
                assertThat(aggregation.results()).hasSize(1);
                assertThat(aggregation.results().get(0).getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
                assertThat(seenExecutionId.get()).isEqualTo("run-123");
                assertThat(seenAssemblyLineId.get()).isEqualTo("pipeline-1");
                assertThat(MDC.get("gear4j.executionId")).isEqualTo("run-123");
                assertThat(MDC.get("gear4j.assemblyLineId")).isEqualTo("pipeline-1");
            } finally {
                MDC.clear();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_shouldPreserveCompletedResultWhenCompletionWinsCancellationRace() {
        // Given
        CancellationToken cancellationToken = new CancellationToken();
        ExecutorService executor = new CompletesDuringCancellationExecutor(cancellationToken);
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                .id("container")
                .withBranch("completed", new TestStation("branch"))
                .build();
        TestStationExecutionContext context = stationContext("container", cancellationToken);

        // When
        ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofSeconds(1));

        // Then
        assertThat(aggregation.results()).hasSize(1);
        StationLogTrace branchLog = aggregation.results().get(0);
        assertThat(branchLog.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(branchLog.<String>getOutput()).isEqualTo("input");
        assertThat(aggregation.interruptingChild()).isNull();
    }

    private static io.github.gear4jtest.core.spi.runner.StationRunner successfulRunner() {
        return (input, station, ctx) -> {
            StationLogTrace log = StationLogTrace.start(ctx.getGlobalContext().getExecutionId(), station.getId(), null);
            log.markSuccess(input);
            return log;
        };
    }

    private static TestStationExecutionContext stationContext(String operationId) {
        return stationContext(operationId, new CancellationToken());
    }

    private static TestStationExecutionContext stationContext(String operationId, CancellationToken cancellationToken) {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "pipeline", Map.of());
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(run.getId())
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(run)
                .cancellationToken(cancellationToken)
                .build();
        return new TestStationExecutionContext(operationId, globalContext,
                StationLogTrace.start(run.getId(), operationId, null), new ExecutionSupport(null, null, null));
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }

    private record TestStationExecutionContext(String operationId,
                                               ExecutionContext globalContext,
                                               StationLogTrace stationLogTrace,
                                               ExecutionSupport support)
            implements EngineStationExecutionContext {
        @Override
        public String getOperationId() {
            return operationId;
        }

        @Override
        public StationKind getKind() {
            return StationKind.CONTAINER;
        }

        @Override
        public ExecutionContext getGlobalContext() {
            return globalContext;
        }

        @Override
        public StationLogTrace getRecord() {
            return stationLogTrace;
        }

        @Override
        public ExecutionSupport getSupport() {
            return support;
        }

        @Override
        public <T> Optional<T> getCapability(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public <T> void addCapability(Class<T> type, T instance) {
            // test context does not expose mutable capabilities
        }

        @Override
        public ResolvedParameters getResolvedParameters() {
            return new ResolvedParameters();
        }
    }

    private static final class CompletesDuringCancellationExecutor extends AbstractExecutorService {
        private final CancellationToken cancellationToken;
        private boolean shutdown;

        private CompletesDuringCancellationExecutor(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
        }

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
            cancellationToken.cancel("test cancellation");
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
            return new FutureTask<>(callable) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    run();
                    return false;
                }
            };
        }
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation(String id) {
            super(id, StationKind.PROCESSING, null, null, null, false, null, null);
        }
    }
}
