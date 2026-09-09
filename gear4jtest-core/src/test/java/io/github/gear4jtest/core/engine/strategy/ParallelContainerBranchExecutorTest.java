package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.assertj.core.api.Assertions.catchThrowable;

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
        assertThat(aggregation.interruptingChild()).contains(branchLog);
    }

    @Test
    void execute_shouldInterruptBranchesNotSubmittedAfterRejectedSubmission() {
        // Given
        ExecutorService rejectedExecutor = Executors.newSingleThreadExecutor();
        rejectedExecutor.shutdown();
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(rejectedExecutor)
                .id("container")
                .withBranch("rejected", new TestStation("rejected"))
                .withBranch("never-submitted-a", new TestStation("never-submitted-a"))
                .withBranch("never-submitted-b", new TestStation("never-submitted-b"))
                .build();
        TestStationExecutionContext context = stationContext("container");

        // When
        ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofMillis(100));

        // Then
        assertThat(aggregation.results())
                .extracting(StationLogTrace::getStatus)
                .containsExactly(StationLogStatus.FAILED, StationLogStatus.CANCELLED, StationLogStatus.CANCELLED);
        assertThat(aggregation.results().subList(1, 3))
                .allMatch(log -> "SIBLING_FLOW_INTERRUPTED".equals(log.getContext().get("synthetic.reason")));
        assertThat(aggregation.interruptingChild()).contains(aggregation.results().get(0));
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
            assertThat(aggregation.interruptingChild()).isEmpty();
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
        assertThat(branchLog.getOutput()).isEqualTo("input");
        assertThat(aggregation.interruptingChild()).isEmpty();
    }

    @Test
    void execute_shouldCancelUnvisitedBranchesWithoutEvaluatingTheirConditions() {
        // Given
        CancellationToken cancellationToken = new CancellationToken();
        ExecutorService executor = new CompletesDuringCancellationExecutor(cancellationToken);
        AtomicInteger unvisitedConditionCalls = new AtomicInteger();
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                .id("container")
                .withBranch("completed", new TestStation("completed"))
                .withBranch("cancellation-observed", new TestStation("cancellation-observed"))
                .withBranch("never-submitted", new TestStation("never-submitted"), (input, context) -> {
                    unvisitedConditionCalls.incrementAndGet();
                    return true;
                })
                .build();
        TestStationExecutionContext context = stationContext("container", cancellationToken);

        // When
        ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofSeconds(1));

        // Then
        assertThat(aggregation.results())
                .extracting(StationLogTrace::getStatus)
                .containsExactly(StationLogStatus.SUCCEEDED, StationLogStatus.CANCELLED,
                                 StationLogStatus.CANCELLED);
        assertThat(aggregation.results().get(1).getContext())
                .containsEntry("synthetic.reason", "COOPERATIVE_CANCELLATION");
        assertThat(aggregation.results().get(2).getContext())
                .containsEntry("synthetic.reason", "CANCELLED_BEFORE_SUBMISSION");
        assertThat(aggregation.results())
                .noneMatch(log -> "FAILED_BEFORE_START".equals(log.getContext().get("synthetic.reason")));
        assertThat(unvisitedConditionCalls).hasValue(0);
        assertThat(aggregation.collectedErrors()).isEmpty();
        assertThat(aggregation.interruptingChild()).contains(aggregation.results().get(1));
    }

    @Test
    void execute_shouldAcceptAwaitTimeoutLargerThanNanosecondRange() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                    .id("container")
                    .withBranch("branch", new TestStation("branch"))
                    .build();
            TestStationExecutionContext context = stationContext("container");

            // When
            ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                    .execute(station, "input", successfulRunner(), context, DEFAULT,
                             Duration.ofSeconds(Long.MAX_VALUE));

            // Then
            assertThat(aggregation.results()).singleElement()
                    .extracting(StationLogTrace::getStatus)
                    .isEqualTo(StationLogStatus.SUCCEEDED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_shouldCancelRunningSiblingWhenLaterPredicateFails() throws Exception {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        IllegalArgumentException failure = new IllegalArgumentException("predicate failed");
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                .id("container")
                .withBranch("running", new TestStation("running"))
                .withBranch("predicate", new TestStation("predicate"), (input, context) -> {
                    awaitStarted(started);
                    throw failure;
                })
                .build();
        try {
            // When
            Throwable actual = catchThrowable(() -> new ParallelContainerBranchExecutor().execute(station, "input",
                                                                                                  (input,
                                                                                                   child,
                                                                                                   context) -> {
                                                                                                      started.countDown();
                                                                                                      try {
                                                                                                          release.await();
                                                                                                      } catch (InterruptedException exception) {
                                                                                                          interrupted
                                                                                                                  .set(true);
                                                                                                          Thread.currentThread()
                                                                                                                  .interrupt();
                                                                                                      } finally {
                                                                                                          finished.countDown();
                                                                                                      }
                                                                                                      return successfulRunner()
                                                                                                              .run(input,
                                                                                                                   child,
                                                                                                                   context);
                                                                                                  },
                                                                                                  stationContext("container"),
                                                                                                  DEFAULT,
                                                                                                  Duration.ofSeconds(5)));

            // Then
            assertThat(actual).isSameAs(failure);
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isTrue();
            assertThat(executor.isShutdown()).isFalse();
            assertThat(executor.submit(() -> "still usable").get(5, TimeUnit.SECONDS)).isEqualTo("still usable");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void execute_shouldCancelRunningSiblingAndPreserveFatalBranchError() throws Exception {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AssertionError failure = new AssertionError("fatal branch");
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                .id("container")
                .withBranch("running", new TestStation("running"))
                .withBranch("fatal", new TestStation("fatal"))
                .build();
        try {
            // When
            Throwable actual = catchThrowable(() -> new ParallelContainerBranchExecutor().execute(station, "input",
                                                                                                  (input,
                                                                                                   child,
                                                                                                   context) -> {
                                                                                                      if ("fatal"
                                                                                                              .equals(child
                                                                                                                      .getId())) {
                                                                                                          awaitStarted(started);
                                                                                                          throw failure;
                                                                                                      }
                                                                                                      started.countDown();
                                                                                                      try {
                                                                                                          release.await();
                                                                                                      } catch (InterruptedException exception) {
                                                                                                          interrupted
                                                                                                                  .set(true);
                                                                                                          Thread.currentThread()
                                                                                                                  .interrupt();
                                                                                                      } finally {
                                                                                                          finished.countDown();
                                                                                                      }
                                                                                                      return successfulRunner()
                                                                                                              .run(input,
                                                                                                                   child,
                                                                                                                   context);
                                                                                                  },
                                                                                                  stationContext("container"),
                                                                                                  DEFAULT,
                                                                                                  Duration.ofSeconds(5)));

            // Then
            assertThat(actual).isSameAs(failure);
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isTrue();
            assertThat(executor.isShutdown()).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void execute_shouldCancelEverySiblingEvenWhenOneCancellationFails() throws Exception {
        // Given
        IllegalStateException cleanupFailure = new IllegalStateException("cancel failed");
        CancellationFailingExecutor executor = new CancellationFailingExecutor(cleanupFailure);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch finished = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger interruptions = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("predicate failed");
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                .id("container")
                .withBranch("first", new TestStation("first"))
                .withBranch("second", new TestStation("second"))
                .withBranch("predicate", new TestStation("predicate"), (input, context) -> {
                    awaitStarted(started);
                    throw failure;
                })
                .build();
        try {
            // When
            Throwable actual = catchThrowable(() -> new ParallelContainerBranchExecutor().execute(station, "input",
                                                                                                  (input,
                                                                                                   child,
                                                                                                   context) -> {
                                                                                                      started.countDown();
                                                                                                      try {
                                                                                                          release.await();
                                                                                                      } catch (InterruptedException exception) {
                                                                                                          interruptions
                                                                                                                  .incrementAndGet();
                                                                                                          Thread.currentThread()
                                                                                                                  .interrupt();
                                                                                                      } finally {
                                                                                                          finished.countDown();
                                                                                                      }
                                                                                                      return successfulRunner()
                                                                                                              .run(input,
                                                                                                                   child,
                                                                                                                   context);
                                                                                                  },
                                                                                                  stationContext("container"),
                                                                                                  DEFAULT,
                                                                                                  Duration.ofSeconds(5)));

            // Then
            assertThat(actual).isSameAs(failure);
            assertThat(actual.getSuppressed()).containsExactly(cleanupFailure);
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interruptions).hasValue(2);
            assertThat(executor.isShutdown()).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitStarted(CountDownLatch started) {
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating branches", exception);
        }
    }

    private static final class CancellationFailingExecutor extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newFixedThreadPool(2);
        private final AtomicInteger createdTasks = new AtomicInteger();
        private final RuntimeException failure;

        private CancellationFailingExecutor(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
            boolean failCancellation = createdTasks.getAndIncrement() == 0;
            return new FutureTask<>(callable) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    boolean cancelled = super.cancel(mayInterruptIfRunning);
                    if (failCancellation) {
                        throw failure;
                    }
                    return cancelled;
                }
            };
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
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
