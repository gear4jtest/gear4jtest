package io.github.gear4jtest.core.engine.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
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

class IfElseContainerStationStrategyTest {
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

    private static StationLogTrace newLog(String operationId) {
        StationLogTrace log = StationLogTrace.start(UUID.randomUUID(), operationId, null);
        log.setContext(new HashMap<>());
        return log;
    }

    @Test
    void should_fail_fast_by_default_when_selected_branch_fails() {
        // Given
        IfElseContainerStationStrategy strategy = new IfElseContainerStationStrategy();
        DummyStation selected = new DummyStation("selected");
        UnaryIfElseContainerStation<String> station = new UnaryIfElseContainerStation.Builder<String>()
                .id("if-else")
                .conditionally("selected-branch", selected, (input, ctx) -> true)
                .build();
        StationExecutionContext context = newOperationExecutionContext("if-else");

        // When
        Object result = strategy.doExecute(station, "input", (input, child, ctx) -> failedLog(child.getId(), "boom"),
                                           context);

        // Then
        assertThat(result).isNull();
        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(context.getRecord().getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void should_ignore_selected_branch_failure_when_failure_policy_allows_it() {
        // Given
        IfElseContainerStationStrategy strategy = new IfElseContainerStationStrategy();
        DummyStation selected = new DummyStation("selected");
        FlowConfig flowConfig = new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                CancelPolicy.PROPAGATE_CANCEL);
        UnaryIfElseContainerStation<String> station = new UnaryIfElseContainerStation.Builder<String>()
                .id("if-else")
                .flowConfig(flowConfig)
                .conditionally("selected-branch", selected, (input, ctx) -> true)
                .build();
        StationExecutionContext context = newOperationExecutionContext("if-else");

        // When
        Object result = strategy.doExecute(station, "input", (input, child, ctx) -> failedLog(child.getId(), "boom"),
                                           context);

        // Then
        assertThat(result).isEqualTo("input");
        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.RUNNING);
    }

    @Test
    void should_collect_selected_branch_failure_and_mark_parent_failed_at_end() {
        // Given
        IfElseContainerStationStrategy strategy = new IfElseContainerStationStrategy();
        DummyStation selected = new DummyStation("selected");
        FlowConfig flowConfig = new FlowConfig(FailurePolicy.COLLECT_AND_FAIL, StopPolicy.PROPAGATE_STOP,
                CancelPolicy.PROPAGATE_CANCEL);
        UnaryIfElseContainerStation<String> station = new UnaryIfElseContainerStation.Builder<String>()
                .id("if-else")
                .flowConfig(flowConfig)
                .conditionally("selected-branch", selected, (input, ctx) -> true)
                .build();
        StationExecutionContext context = newOperationExecutionContext("if-else");

        // When
        Object result = strategy.doExecute(station, "input", (input, child, ctx) -> failedLog(child.getId(), "boom"),
                                           context);

        // Then
        assertThat(result).isEqualTo("input");
        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(context.getRecord().getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void should_run_selected_branch_in_branch_scope_and_normalize_child_trace() {
        // Given
        IfElseContainerStationStrategy strategy = new IfElseContainerStationStrategy();
        DummyStation selected = new DummyStation("selected");
        UnaryIfElseContainerStation<String> station = new UnaryIfElseContainerStation.Builder<String>()
                .id("if-else")
                .conditionally("selected-branch", selected, (input, ctx) -> true)
                .build();
        StationExecutionContext context = newOperationExecutionContext("if-else");
        AtomicReference<String> branchScopeSeenByRunner = new AtomicReference<>();
        AtomicReference<StationLogTrace> childLog = new AtomicReference<>();
        StationRunner runner = (input, child, ctx) -> {
            branchScopeSeenByRunner.set(ctx.getGlobalContext().getCurrentBranchId());
            StationLogTrace log = successLog(child.getId(), "output");
            childLog.set(log);
            return log;
        };

        // When
        Object result = strategy.doExecute(station, "input", runner, context);

        // Then
        assertThat(result).isEqualTo("output");
        assertThat(branchScopeSeenByRunner.get()).isEqualTo("selected-branch");
        assertThat(context.getGlobalContext().getCurrentBranchId()).isNull();
        assertThat(childLog.get().getParentOperationId()).isEqualTo(context.getRecord().getId());
        assertThat(childLog.get().getBranchId()).isEqualTo("selected-branch");
    }

    @Test
    void should_run_else_branch_when_no_condition_matches() {
        // Given
        IfElseContainerStationStrategy strategy = new IfElseContainerStationStrategy();
        DummyStation skipped = new DummyStation("skipped");
        DummyStation elseStation = new DummyStation("else");
        UnaryIfElseContainerStation<String> station = new UnaryIfElseContainerStation.Builder<String>()
                .id("if-else")
                .conditionally("skipped-branch", skipped, (input, ctx) -> false)
                .elseOp("else-branch", elseStation);
        StationExecutionContext context = newOperationExecutionContext("if-else");
        AtomicReference<String> branchScopeSeenByRunner = new AtomicReference<>();
        StationRunner runner = (input, child, ctx) -> {
            branchScopeSeenByRunner.set(ctx.getGlobalContext().getCurrentBranchId());
            return successLog(child.getId(), "else-output");
        };

        // When
        Object result = strategy.doExecute(station, "input", runner, context);

        // Then
        assertThat(result).isEqualTo("else-output");
        assertThat(branchScopeSeenByRunner.get()).isEqualTo("else-branch");
    }

    private static final class DummyStation extends AbstractStation<String, String> {
        private DummyStation(String id) {
            super(id, StationKind.CUSTOM, null, null, null, true, null, null);
        }
    }
}
