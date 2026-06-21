package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.pipeline.PipelineReference;
import io.github.gear4jtest.core.api.pipeline.ReferencedPipelineTarget;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.exception.PipelineCallException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineCallStationStrategyStatusMappingTest {
    @Test
    void doExecute_shouldRejectUnresolvedTargetsBeforeExecution() {
        // Given
        PipelineCallStation<String, String> station = PipelineCallStation.<String, String>builder("call")
                .inline()
                .target(new ReferencedPipelineTarget<>(new PipelineReference("missing", "1")))
                .build();
        PipelineCallStationStrategy strategy = new PipelineCallStationStrategy(NestedPipelineExecutor.unsupported());

        StationRunner runner = successfulRunner();
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> strategy.doExecute(station, "input", runner, context))
                .isInstanceOf(PipelineCallException.class)
                .hasMessageContaining("is not resolved")
                .hasMessageContaining("missing:1");
    }

    @Test
    void doExecute_shouldMapInlineSkippedChildToSkippedCallLogAndKeepOutput() {
        // Given
        StationLogTrace childLog = childLog(StationLogStatus.SKIPPED, "child-output", null);
        PipelineCallStation<String, String> station = PipelineCallStation.inline("call", childPipeline());
        TestStationExecutionContext context = stationContext("call");
        PipelineCallStationStrategy strategy = new PipelineCallStationStrategy(NestedPipelineExecutor.unsupported());

        // When
        Object output = strategy.doExecute(station, "input", runnerReturning(childLog), context);

        // Then
        assertThat(output).isEqualTo("child-output");
        assertThat(context.stationLogTrace().getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(context.stationLogTrace().<String>getOutput()).isEqualTo("child-output");
        assertThat(context.stationLogTrace().getContext()).containsEntry("pipeline.call.mode", "INLINE")
                .containsEntry("pipeline.call.declaredReference", "child:1")
                .containsEntry("pipeline.call.resolvedReference", "child:1")
                .containsEntry("skip.reason", "Child pipeline root was skipped: child");
    }

    @Test
    void doExecute_shouldMapInlineStoppedAndCancelledChildrenToCallLogStatuses() {
        // Given
        PipelineCallStation<String, String> station = PipelineCallStation.inline("call", childPipeline());
        PipelineCallStationStrategy strategy = new PipelineCallStationStrategy(NestedPipelineExecutor.unsupported());

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
        PipelineCallStation<String, String> station = PipelineCallStation.inline("call", childPipeline());
        PipelineCallStationStrategy strategy = new PipelineCallStationStrategy(NestedPipelineExecutor.unsupported());

        StationRunner runner = runnerReturning(childLog(StationLogStatus.FAILED, null, failure));
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> strategy.doExecute(station, "input", runner, context))
                .isInstanceOf(PipelineCallException.class)
                .satisfies(throwable -> assertThat(throwable.getCause()).isSameAs(failure))
                .hasMessageContaining("Inline child pipeline 'child:1' failed in station 'call'");
    }

    @Test
    void doExecute_shouldMapNestedSkippedStoppedAndCancelledChildrenToCallLogStatuses() {
        // Given
        PipelineCallStation<String, String> station = PipelineCallStation.nestedRun("call", childPipeline());
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
        assertThat(skippedContext.stationLogTrace().getContext()).containsEntry("pipeline.call.childExecutionId",
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
        PipelineCallStation<String, String> station = PipelineCallStation.nestedRun("call", childPipeline());
        IllegalStateException failure = new IllegalStateException("nested failed");

        AssemblyRunTrace failedRun = childRun(ExecutionStatus.FAILED, failure);
        PipelineCallStationStrategy failedStrategy = strategyReturning(ExecutionResult.failure(failure, failedRun));
        StationRunner runner = successfulRunner();
        TestStationExecutionContext context = stationContext("call");

        // When / Then
        assertThatThrownBy(() -> failedStrategy.doExecute(station, "input", runner, context))
                .isInstanceOf(PipelineCallException.class)
                .satisfies(throwable -> assertThat(throwable.getCause()).isSameAs(failure))
                .hasMessageContaining("Nested child pipeline 'child:1' failed in station 'call'");
        ExecutionResult<Object> failedWithoutRun = new ExecutionResult<>(null,
                io.github.gear4jtest.core.api.ExecutionOutcome.FAILED, null, null);
        PipelineCallStationStrategy failedWithoutRunStrategy = strategyReturning(failedWithoutRun);
        TestStationExecutionContext contextWithoutRun = stationContext("call");

        assertThatThrownBy(() -> failedWithoutRunStrategy.doExecute(station, "input", runner, contextWithoutRun))
                .isInstanceOf(PipelineCallException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Nested child pipeline 'child:1' failed in station 'call'");
    }

    private static PipelineCallStationStrategy strategyReturning(ExecutionResult<?> result) {
        return new PipelineCallStationStrategy((station, childPipeline, input, parentContext) -> result);
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

    private static AssemblyLine<String, String> childPipeline() {
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
                .pipelineId("parent")
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
            return StationKind.PIPELINE;
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
            return null;
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
