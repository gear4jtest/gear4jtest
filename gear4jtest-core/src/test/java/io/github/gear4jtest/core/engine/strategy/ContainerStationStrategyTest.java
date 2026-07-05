package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.ContainerBranch;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.ExecutorDecorator;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerStationStrategyTest {
    private static StationExecutionContext newOperationExecutionContext(String operationId) {
        AssemblyRunTrace assemblyRun = new AssemblyRunTrace(UUID.randomUUID(), "pipeline-1", Map.of());
        var resourceFactory = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline-1")
                .services(new ExecutionServices(
                        new EventManager(EventHandlingDefinition.builder().build(), new ExecutionContextRegistry()),
                        resourceFactory))
                .assemblyRun(assemblyRun)
                .build();

        StationLogTrace parentRecord = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        parentRecord.setContext(new HashMap<>());
        parentRecord.setStatus(StationLogStatus.RUNNING);

        return new DefaultStationExecutionContext(operationId, StationKind.CONTAINER, globalContext, parentRecord,
                new ExecutionSupport(ExecutorDecorator.noOp(), new TaskFactory(), null));
    }

    private static StationLogTrace successLog(String operationId, Object output) {
        StationLogTrace log = newLog(operationId);
        log.markSuccess(output);
        return log;
    }

    private static StationLogTrace failedLog(String operationId, String message) {
        StationLogTrace log = newLog(operationId);
        log.markFailed(new RuntimeException(message));
        return log;
    }

    private static StationLogTrace cancelledLog(String operationId, String message) {
        StationLogTrace log = newLog(operationId);
        log.markCancelled(new RuntimeException(message));
        return log;
    }

    private static StationLogTrace newLog(String operationId) {
        StationLogTrace log = StationLogTrace.start(UUID.randomUUID(), operationId, null);
        log.setContext(new HashMap<>());
        return log;
    }

    private static DummyStation station(String id) {
        return new DummyStation(id);
    }

    private static TypedDummyStation<String> stringStation(String id) {
        return new TypedDummyStation<>(id);
    }

    @Test
    void should_reject_branch_without_explicit_id() {
        // Given
        DummyStation station = station("station");

        ContainerBaseStation.Branch.Builder<Object> builder = new ContainerBaseStation.Branch.Builder<Object>()
                .withOperation(station);

        // When / Then
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("branch id is required");
    }

    @Test
    void should_fail_fast_and_stop_next_branch_by_default_in_sequential_container() {
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        DummyStation first = station("first");
        DummyStation second = station("second");

        var container = new ContainerBaseStation.Builder<>()
                .id("container")
                .withBranch("1", first)
                .withBranch("2", second)
                .build();

        StationRunner runner = mock(StationRunner.class);
        StationExecutionContext operationExecution = newOperationExecutionContext("container");

        when(runner.run(any(), same(first), same(operationExecution))).thenReturn(failedLog("first", "boom"));

        // When
        Object result = strategy.doExecute(container, "input", runner, operationExecution);

        // Then
        assertThat(result).isNull();
        assertThat(operationExecution.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(operationExecution.getRecord().getErrorMessage()).isEqualTo("boom");

        verify(runner).run(any(), same(first), same(operationExecution));
        verify(runner, never()).run(any(), same(second), same(operationExecution));
    }

    @Test
    void should_ignore_failed_branch_and_continue_in_sequential_container() {
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        DummyStation first = station("first");
        DummyStation second = station("second");

        FlowConfig flowConfig = new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                CancelPolicy.PROPAGATE_CANCEL);

        var container = new ContainerBaseStation.Builder<Object, Object>().id("container").flowConfig(flowConfig)
                .withBranch("1", first).withBranch("2", second).returns(results -> results.orderedOutputs());

        StationRunner runner = mock(StationRunner.class);
        StationExecutionContext operationExecution = newOperationExecutionContext("container");

        when(runner.run(any(), same(first), same(operationExecution))).thenReturn(failedLog("first", "boom"));
        when(runner.run(any(), same(second), same(operationExecution))).thenReturn(successLog("second", "B"));

        // When
        Object result = strategy.doExecute(container, "input", runner, operationExecution);

        // Then
        assertThat(result).isEqualTo(Arrays.asList(null, "B"));
        assertThat(operationExecution.getRecord().getStatus())
                .isNotIn(StationLogStatus.FAILED, StationLogStatus.CANCELLED, StationLogStatus.STOPPED);

        verify(runner).run(any(), same(first), same(operationExecution));
        verify(runner).run(any(), same(second), same(operationExecution));
    }

    @Test
    void should_collect_errors_and_fail_at_the_end_in_sequential_container() {
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        DummyStation first = station("first");
        DummyStation second = station("second");

        FlowConfig flowConfig = new FlowConfig(FailurePolicy.COLLECT_AND_FAIL, StopPolicy.PROPAGATE_STOP,
                CancelPolicy.PROPAGATE_CANCEL);

        var container = new ContainerBaseStation.Builder<Object, Object>().id("container").flowConfig(flowConfig)
                .withBranch("1", first).withBranch("2", second).returns(results -> results.orderedOutputs());

        StationRunner runner = mock(StationRunner.class);
        StationExecutionContext operationExecution = newOperationExecutionContext("container");

        when(runner.run(any(), same(first), same(operationExecution))).thenReturn(failedLog("first", "boom"));
        when(runner.run(any(), same(second), same(operationExecution))).thenReturn(successLog("second", "B"));

        // When
        Object result = strategy.doExecute(container, "input", runner, operationExecution);

        // Then
        assertThat(result).isEqualTo(Arrays.asList(null, "B"));
        assertThat(operationExecution.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(operationExecution.getRecord().getErrorMessage()).isEqualTo("boom");

        verify(runner).run(any(), same(first), same(operationExecution));
        verify(runner).run(any(), same(second), same(operationExecution));
    }

    @Test
    void should_keep_branch_slot_as_null_when_condition_is_false() {
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        DummyStation skipped = station("skipped");
        DummyStation executed = station("executed");

        var container = new ContainerBaseStation.Builder<Object, Object>()
                .id("container")
                .withBranch("1", skipped, (input, ctx) -> false).withBranch("2", executed)
                .returns(results -> results.orderedOutputs());

        StationRunner runner = mock(StationRunner.class);
        StationExecutionContext operationExecution = newOperationExecutionContext("container");

        when(runner.run(any(), same(executed), same(operationExecution))).thenReturn(successLog("executed", "B"));

        // When
        Object result = strategy.doExecute(container, "input", runner, operationExecution);

        // Then
        assertThat(result).isEqualTo(Arrays.asList(null, "B"));

        verify(runner, never()).run(any(), same(skipped), same(operationExecution));
        verify(runner).run(any(), same(executed), same(operationExecution));
    }

    @Test
    void should_assemble_named_container_results_from_more_than_two_branches() {
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        TypedDummyStation<String> first = stringStation("first");
        TypedDummyStation<String> second = stringStation("second");
        TypedDummyStation<String> third = stringStation("third");
        ContainerBranch<Object, String> left = ContainerBranch.of("left", first);
        ContainerBranch<Object, String> middle = ContainerBranch.of("middle", second);
        ContainerBranch<Object, String> right = ContainerBranch.of("right", third);

        var container = new ContainerBaseStation.Builder<Object, Object>()
                .id("container")
                .withBranch(left)
                .withBranch(middle)
                .withBranch(right)
                .returns(results -> results.get(left) + results.get(middle) + results.get(right));

        StationRunner runner = mock(StationRunner.class);
        StationExecutionContext operationExecution = newOperationExecutionContext("container");

        when(runner.run(any(), same(first), same(operationExecution))).thenReturn(successLog("first", "A"));
        when(runner.run(any(), same(second), same(operationExecution))).thenReturn(successLog("second", "B"));
        when(runner.run(any(), same(third), same(operationExecution))).thenReturn(successLog("third", "C"));

        // When
        Object result = strategy.doExecute(container, "input", runner, operationExecution);

        // Then
        assertThat(result).isEqualTo("ABC");
        verify(runner).run(any(), same(first), same(operationExecution));
        verify(runner).run(any(), same(second), same(operationExecution));
        verify(runner).run(any(), same(third), same(operationExecution));
    }

    @Test
    void should_cancel_parent_when_parallel_branch_times_out_with_default_cancel_policy() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            // Given
            ContainerStationStrategy strategy = new ContainerStationStrategy();
            DummyStation slow = station("slow");
            DummyStation fast = station("fast");

            var container = new ContainerBaseStation.Builder<Object, Object>(executorService)
                    .id("container")
                    .awaitTimeout(Duration.ofMillis(50)).withBranch("1", slow).withBranch("2", fast)
                    .returns(results -> results.orderedOutputs());

            StationExecutionContext operationExecution = newOperationExecutionContext("container");

            StationRunner runner = (input, station, ctx) -> {
                if ("slow".equals(station.getId())) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return successLog("slow", "A");
                }
                return successLog("fast", "B");
            };

            // When
            Object result = strategy.doExecute(container, "input", runner, operationExecution);

            // Then
            assertThat(result).isNull();
            assertThat(operationExecution.getRecord().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void should_ignore_parallel_timeout_and_keep_null_slot_when_cancel_policy_allows_it() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            // Given
            ContainerStationStrategy strategy = new ContainerStationStrategy();
            DummyStation slow = station("slow");
            DummyStation fast = station("fast");

            FlowConfig flowConfig = new FlowConfig(FailurePolicy.FAIL_FAST, StopPolicy.PROPAGATE_STOP,
                    CancelPolicy.IGNORE_AND_CONTINUE);

            var container = new ContainerBaseStation.Builder<Object, Object>(executorService)
                    .id("container")
                    .flowConfig(flowConfig)
                    .awaitTimeout(Duration.ofMillis(50)).withBranch("1", slow).withBranch("2", fast)
                    .returns(results -> results.orderedOutputs());

            StationExecutionContext operationExecution = newOperationExecutionContext("container");

            StationRunner runner = (input, station, ctx) -> {
                if ("slow".equals(station.getId())) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return successLog("slow", "A");
                }
                return successLog("fast", "B");
            };

            // When
            Object result = strategy.doExecute(container, "input", runner, operationExecution);

            // Then
            assertThat(result).isEqualTo(Arrays.asList(null, "B"));
            assertThat(operationExecution.getRecord().getStatus())
                    .isNotIn(StationLogStatus.FAILED, StationLogStatus.CANCELLED, StationLogStatus.STOPPED);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void should_interrupt_parallel_container_without_waiting_for_all_branches_when_fail_fast() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            // Given
            ContainerStationStrategy strategy = new ContainerStationStrategy();
            DummyStation slow = station("slow");
            DummyStation failing = station("failing");

            var container = new ContainerBaseStation.Builder<Object, Object>(executorService)
                    .id("container")
                    .withBranch("1", slow)
                    .withBranch("2", failing).returns(results -> results.orderedOutputs());

            StationExecutionContext operationExecution = newOperationExecutionContext("container");

            CountDownLatch bothStarted = new CountDownLatch(2);
            CountDownLatch slowInterrupted = new CountDownLatch(1);

            StationRunner runner = (input, station, ctx) -> {
                try {
                    if ("slow".equals(station.getId())) {
                        bothStarted.countDown();
                        bothStarted.await();

                        try {
                            Thread.sleep(5_000L);
                        } catch (InterruptedException e) {
                            slowInterrupted.countDown();
                            Thread.currentThread().interrupt();
                            return cancelledLog("slow", "slow interrupted");
                        }

                        return successLog("slow", "A");
                    }

                    bothStarted.countDown();
                    bothStarted.await();
                    return failedLog("failing", "boom");
                } catch (InterruptedException e) {
                    slowInterrupted.countDown();
                    Thread.currentThread().interrupt();
                    return cancelledLog("slow", "slow interrupted");
                }
            };

            // When
            long startNanos = System.nanoTime();
            Object result = strategy.doExecute(container, "input", runner, operationExecution);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // Then
            assertThat(result).isNull();
            assertThat(operationExecution.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
            assertThat(operationExecution.getRecord().getErrorMessage()).isEqualTo("boom");
            assertThat(elapsedMillis).isLessThan(1_500L);
            assertThat(slowInterrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void should_apply_engine_default_timeout_when_parallel_station_has_no_override() {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            // Given
            ContainerStationStrategy strategy = new ContainerStationStrategy(
                    ParallelExecutionConfiguration.withDefaultAwaitTimeout(Duration.ofMillis(30)));
            DummyStation slow = station("slow");
            var container = new ContainerBaseStation.Builder<Object, Object>(executorService)
                    .id("container")
                    .withBranch("1", slow)
                    .returns(results -> results.orderedOutputs());
            StationExecutionContext context = newOperationExecutionContext("container");
            StationRunner runner = (input, station, ctx) -> {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return successLog("slow", "A");
            };

            // When
            strategy.doExecute(container, "input", runner, context);

            // Then
            assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void should_mark_container_failed_when_executor_rejects_branch_submission() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.shutdownNow();
        // Given
        ContainerStationStrategy strategy = new ContainerStationStrategy();
        DummyStation branch = station("rejected");
        var container = new ContainerBaseStation.Builder<Object, Object>(executorService)
                .id("container")
                .withBranch("1", branch)
                .returns(results -> results.orderedOutputs());
        StationExecutionContext context = newOperationExecutionContext("container");

        // When
        Object result = strategy.doExecute(container, "input", (input, station, ctx) -> successLog("ignored", "A"),
                                           context);

        // Then
        assertThat(result).isNull();
        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(context.getRecord().getThrowables()).anyMatch(RejectedExecutionException.class::isInstance);
    }

    private static final class TypedDummyStation<T> extends AbstractStation<Object, T> {
        private TypedDummyStation(String id) {
            super(id, StationKind.CUSTOM, null, null, null, false, null, null);
        }
    }

    private static final class DummyStation extends AbstractStation<Object, Object> {
        private DummyStation(String id) {
            super(id, StationKind.CUSTOM, null, null, null, false, null, null);
        }
    }
}
