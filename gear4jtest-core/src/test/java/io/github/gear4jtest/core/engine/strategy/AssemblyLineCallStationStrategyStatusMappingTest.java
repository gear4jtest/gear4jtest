package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineReference;
import io.github.gear4jtest.core.api.assemblyline.ReferencedAssemblyLineTarget;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.exception.AssemblyLineCallException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineCallStationStrategyStatusMappingTest {
    @Test
    void doExecute_shouldRejectUnresolvedTargetsBeforeExecution() {
        // Given
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.<String, String>builder("call")
                .inline()
                .target(new ReferencedAssemblyLineTarget<>(new AssemblyLineReference("missing", "1")))
                .build();
        AssemblyLineCallStationStrategy strategy = new AssemblyLineCallStationStrategy(
                NestedAssemblyLineExecutor.unsupported());

        StationRunner runner = successfulRunner();
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> strategy.doExecute(station, "input", runner, context))
                .isInstanceOf(AssemblyLineCallException.class)
                .hasMessageContaining("is not resolved")
                .hasMessageContaining("missing:1");
    }

    @Test
    void doExecute_shouldMapInlineSkippedChildToSkippedCallLogAndKeepOutput() {
        // Given
        StationLogTrace childLog = childLog(StationLogStatus.SKIPPED, "child-output", null);
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.inline("call", childAssemblyLine());
        TestStationExecutionContext context = stationContext("call");
        AssemblyLineCallStationStrategy strategy = new AssemblyLineCallStationStrategy(
                NestedAssemblyLineExecutor.unsupported());

        // When
        Object output = strategy.doExecute(station, "input", runnerReturning(childLog), context);

        // Then
        assertThat(output).isEqualTo("child-output");
        assertThat(context.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(context.stationLogTrace().<String>getOutput()).isEqualTo("child-output");
        assertThat(context.stationLogTrace().getContext()).containsEntry("assemblyLine.call.mode", "INLINE")
                .containsEntry("assemblyLine.call.declaredReference", "child:1")
                .containsEntry("assemblyLine.call.resolvedReference", "child:1")
                .containsEntry("skip.reason", "Child assembly line root was skipped: child");
    }

    @Test
    void doExecute_shouldMapInlineStoppedAndCancelledChildrenToCallLogStatuses() {
        // Given
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.inline("call", childAssemblyLine());
        AssemblyLineCallStationStrategy strategy = new AssemblyLineCallStationStrategy(
                NestedAssemblyLineExecutor.unsupported());

        // When
        TestStationExecutionContext stoppedContext = stationContext("call-stopped");
        Object stoppedOutput = strategy.doExecute(station, "input",
                                                  runnerReturning(childLog(StationLogStatus.STOPPED, "stopped",
                                                                           new IllegalStateException("stop"))),
                                                  stoppedContext);
        TestStationExecutionContext cancelledContext = stationContext("call-cancelled");
        Object cancelledOutput = strategy.doExecute(station, "input",
                                                    runnerReturning(childLog(StationLogStatus.CANCELLED, "cancelled",
                                                                             new IllegalStateException("cancel"))),
                                                    cancelledContext);

        // Then
        assertThat(stoppedOutput).isEqualTo("stopped");
        assertThat(stoppedContext.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.STOPPED);
        assertThat(stoppedContext.stationLogTrace().getErrorMessage()).isEqualTo("stop");
        assertThat(cancelledOutput).isEqualTo("cancelled");
        assertThat(cancelledContext.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
        assertThat(cancelledContext.stationLogTrace().getErrorMessage()).isEqualTo("cancel");
    }

    @Test
    void doExecute_shouldThrowWhenInlineChildFails() {
        // Given
        IllegalArgumentException failure = new IllegalArgumentException("child failed");
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.inline("call", childAssemblyLine());
        AssemblyLineCallStationStrategy strategy = new AssemblyLineCallStationStrategy(
                NestedAssemblyLineExecutor.unsupported());

        StationRunner runner = runnerReturning(childLog(StationLogStatus.FAILED, null, failure));
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> strategy.doExecute(station, "input", runner, context))
                .isInstanceOf(AssemblyLineCallException.class)
                .satisfies(throwable -> assertThat(throwable.getCause()).isSameAs(failure))
                .hasMessageContaining("Inline child assembly line 'child:1' failed in station 'call'");
    }

    @Test
    void doExecute_shouldMapNestedSkippedStoppedAndCancelledChildrenToCallLogStatuses() {
        // Given
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.nestedRun("call",
                                                                                            childAssemblyLine());
        AssemblyRunTrace skipped = childRun(ExecutionStatus.SKIPPED, null);
        AssemblyRunTrace stopped = childRun(ExecutionStatus.STOPPED, new IllegalStateException("stop"));
        AssemblyRunTrace cancelled = childRun(ExecutionStatus.CANCELLED, new IllegalStateException("cancel"));

        // When
        TestStationExecutionContext skippedContext = stationContext("nested-skipped");
        Object skippedOutput = strategyReturning(ExecutionResult.skipped("skipped", skipped))
                .doExecute(station, "input", successfulRunner(), skippedContext);
        TestStationExecutionContext stoppedContext = stationContext("nested-stopped");
        Object stoppedOutput = strategyReturning(ExecutionResult.stopped("stopped", stopped))
                .doExecute(station, "input", successfulRunner(), stoppedContext);
        TestStationExecutionContext cancelledContext = stationContext("nested-cancelled");
        Object cancelledOutput = strategyReturning(ExecutionResult.cancelled("cancelled", cancelled,
                                                                             new IllegalStateException("cancel")))
                .doExecute(station, "input", successfulRunner(), cancelledContext);

        // Then
        assertThat(skippedOutput).isEqualTo("skipped");
        assertThat(skippedContext.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(skippedContext.stationLogTrace().getContext()).containsEntry("assemblyLine.call.childExecutionId",
                                                                                skipped.getId());
        assertThat(stoppedOutput).isEqualTo("stopped");
        assertThat(stoppedContext.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.STOPPED);
        assertThat(stoppedContext.stationLogTrace().getErrorMessage()).isEqualTo("stop");
        assertThat(cancelledOutput).isEqualTo("cancelled");
        assertThat(cancelledContext.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
        assertThat(cancelledContext.stationLogTrace().getErrorMessage()).isEqualTo("cancel");
    }

    @Test
    void doExecute_shouldThrowWhenNestedChildFailsOrHasNoTerminalExecution() {
        // Given
        AssemblyLineCallStation<String, String> station = AssemblyLineCallStation.nestedRun("call",
                                                                                            childAssemblyLine());
        IllegalStateException failure = new IllegalStateException("nested failed");

        AssemblyRunTrace failedRun = childRun(ExecutionStatus.FAILED, failure);
        AssemblyLineCallStationStrategy failedStrategy = strategyReturning(ExecutionResult.failure(failure, failedRun));
        StationRunner runner = successfulRunner();
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> failedStrategy.doExecute(station, "input", runner, context))
                .isInstanceOf(AssemblyLineCallException.class)
                .satisfies(throwable -> assertThat(throwable.getCause()).isSameAs(failure))
                .hasMessageContaining("Nested child assembly line 'child:1' failed in station 'call'");
        ExecutionResult<Object> failedWithoutRun = new ExecutionResult<>(null,
                io.github.gear4jtest.core.api.ExecutionOutcome.FAILED, null, null);
        AssemblyLineCallStationStrategy failedWithoutRunStrategy = strategyReturning(failedWithoutRun);
        TestStationExecutionContext contextWithoutRun = stationContext("call");

        assertThatThrownBy(() -> failedWithoutRunStrategy.doExecute(station, "input", runner, contextWithoutRun))
                .isInstanceOf(AssemblyLineCallException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Nested child assembly line 'child:1' failed in station 'call'");
    }

    private static AssemblyLineCallStationStrategy strategyReturning(ExecutionResult<?> result) {
        return new AssemblyLineCallStationStrategy((station, childAssemblyLine, input, parentContext) -> result);
    }

    private static StationRunner runnerReturning(StationLogTrace log) {
        return (input, station, ctx) -> log;
    }

    private static StationRunner successfulRunner() {
        return (input, station, ctx) -> {
            StationLogTrace log = StationLogTrace.start(ctx.getGlobalContext().getExecutionId(), station.getId(), null);
            log.markSuccess(input);
            return log;
        };
    }

    private static AssemblyLine<String, String> childAssemblyLine() {
        return AssemblyLine.<String, String>builder("child").build();
    }

    private static StationLogTrace childLog(StationLogStatus status, Object output, Exception exception) {
        StationLogTrace log = StationLogTrace.start(UUID.randomUUID(), "child-root", null);
        switch (status) {
            case SUCCEEDED -> log.markSuccess(output);
            case SKIPPED -> {
                log.markSkipped("skipped by test");
                log.setOutput(output);
            }
            case STOPPED -> {
                log.markStopped(exception);
                log.setOutput(output);
            }
            case CANCELLED -> {
                log.markCancelled(exception);
                log.setOutput(output);
            }
            case FAILED -> log.markFailed(exception);
            case RUNNING -> log.setOutput(output);
        }
        return log;
    }

    private static AssemblyRunTrace childRun(ExecutionStatus status, Exception error) {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "child", Map.of());
        run.setStatus(status);
        run.setError(error);
        return run;
    }

    private static TestStationExecutionContext stationContext(String operationId) {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "parent", Map.of());
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(run.getId())
                .assemblyLineId("parent")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(run)
                .build();
        return new TestStationExecutionContext(operationId, globalContext,
                StationLogTrace.start(globalContext.getExecutionId(), operationId, null));
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
                                               StationLogTrace stationLogTrace)
            implements StationExecutionContext {
        @Override
        public String getOperationId() {
            return operationId;
        }

        @Override
        public StationKind getKind() {
            return StationKind.ASSEMBLY_LINE;
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
        public <T> Optional<T> getCapability(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public ResolvedParameters getResolvedParameters() {
            return new ResolvedParameters();
        }
    }
}
