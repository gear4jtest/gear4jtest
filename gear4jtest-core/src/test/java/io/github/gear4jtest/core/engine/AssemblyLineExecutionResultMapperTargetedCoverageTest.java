package io.github.gear4jtest.core.engine;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineExecutionResultMapperTargetedCoverageTest {
    @ParameterizedTest
    @MethodSource("terminalRootStatuses")
    void executeRootStation_shouldMapEveryTerminalStationStatus(StationLogStatus stationStatus,
                                                                ExecutionOutcome expectedOutcome,
                                                                ExecutionStatus expectedStatus) {
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipe")
                .then(new TestStation())
                .build();
        RunRequest request = RunRequest.builder().input("input").build();
        ExecutionContext globalContext = executionContext();
        StationExecutionContext rootContext = stationContext(globalContext);
        AssemblyRunTrace execution = new AssemblyRunTrace(globalContext.getExecutionId(), "pipe", Map.of());
        StationRunner rootRunner = (input, station, ctx) -> rootLog(globalContext.getExecutionId(), stationStatus);

        ExecutionResult<String> result = AssemblyLineExecutionResultMapper
                .executeRootStation(pipeline, request, rootRunner, rootContext, execution);

        assertThat(result.getOutcome()).isEqualTo(expectedOutcome);
        assertThat(execution.getStatus()).isEqualTo(expectedStatus);
        if (expectedOutcome == ExecutionOutcome.FAILED) {
            assertThat(result.getError()).isNotNull();
            assertThat(execution.getError()).isNotNull();
        } else {
            assertThat(result.getResult()).isEqualTo("output");
            assertThat(execution.getResult()).isEqualTo("output");
        }
    }

    @Test
    void executeRootStation_shouldCreateCancellationErrorFromRootLogMessage() {
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipe")
                .then(new TestStation())
                .build();
        ExecutionContext globalContext = executionContext();
        StationExecutionContext rootContext = stationContext(globalContext);
        AssemblyRunTrace execution = new AssemblyRunTrace(globalContext.getExecutionId(), "pipe", Map.of());
        StationRunner rootRunner = (input, station, ctx) -> {
            StationLogTrace trace = StationLogTrace.start(globalContext.getExecutionId(), "root", null);
            trace.markCancelled(new RuntimeException("cancelled by caller"));
            trace.setOutput("partial");
            return trace;
        };

        ExecutionResult<String> result = AssemblyLineExecutionResultMapper.executeRootStation(pipeline,
                                                                                              RunRequest.builder()
                                                                                                      .input("input")
                                                                                                      .build(),
                                                                                              rootRunner, rootContext,
                                                                                              execution);

        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.CANCELLED);
        assertThat(result.getError()).hasMessage("cancelled by caller");
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(execution.getError()).hasMessage("cancelled by caller");
    }

    @Test
    void executeRootStation_shouldExposeNonTerminalRunningAsEngineBug() {
        AssemblyLine<String, String> pipeline = AssemblyLine.<String, String>builder("pipe")
                .then(new TestStation())
                .build();
        ExecutionContext globalContext = executionContext();
        StationExecutionContext rootContext = stationContext(globalContext);
        AssemblyRunTrace execution = new AssemblyRunTrace(globalContext.getExecutionId(), "pipe", Map.of());
        StationRunner rootRunner = (input, station, ctx) -> rootLog(globalContext.getExecutionId(),
                                                                    StationLogStatus.RUNNING);

        ExecutionResult<String> result = AssemblyLineExecutionResultMapper.executeRootStation(pipeline,
                                                                                              RunRequest.builder()
                                                                                                      .input("input")
                                                                                                      .build(),
                                                                                              rootRunner, rootContext,
                                                                                              execution);

        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(result.getError()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-terminal RUNNING");
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getError()).isSameAs(result.getError());
    }

    @Test
    void finalizeRunFromResult_shouldHandleFatalErrorsNullResultsAndNonExceptionThrowables() {
        ExecutionContext context = executionContext();
        AssemblyRunTrace fatal = new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of());
        Error error = new Error("fatal");

        AssemblyLineExecutionResultMapper.finalizeRunFromResult(context, fatal, null, error);

        assertThat(fatal.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(fatal.getErrorMessage()).contains("CRITICAL JVM ERROR");

        AssemblyRunTrace nullResult = new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of());
        AssemblyLineExecutionResultMapper.finalizeRunFromResult(context, nullResult, null, null);

        assertThat(nullResult.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(nullResult.getError()).isInstanceOf(IllegalStateException.class);

        assertThat(AssemblyLineExecutionResultMapper.asException(error))
                .isInstanceOf(RuntimeException.class)
                .hasCause(error);
    }

    private static Stream<Arguments> terminalRootStatuses() {
        return Stream.of(
                         Arguments.of(StationLogStatus.SUCCEEDED, ExecutionOutcome.SUCCEEDED,
                                      ExecutionStatus.SUCCEEDED),
                         Arguments.of(StationLogStatus.SKIPPED, ExecutionOutcome.SKIPPED, ExecutionStatus.SKIPPED),
                         Arguments.of(StationLogStatus.STOPPED, ExecutionOutcome.STOPPED, ExecutionStatus.STOPPED),
                         Arguments.of(StationLogStatus.FAILED, ExecutionOutcome.FAILED, ExecutionStatus.FAILED));
    }

    private static StationLogTrace rootLog(UUID executionId, StationLogStatus status) {
        StationLogTrace trace = StationLogTrace.start(executionId, "root", null);
        switch (status) {
            case SUCCEEDED -> trace.markSuccess("output");
            case SKIPPED -> {
                trace.markSkipped();
                trace.setOutput("output");
            }
            case STOPPED -> {
                trace.markStopped(null);
                trace.setOutput("output");
            }
            case FAILED -> trace.markFailed(new RuntimeException("root failed"));
            case CANCELLED -> trace.markCancelled(new RuntimeException("cancelled"));
            case RUNNING -> trace.setOutput("output");
        }
        return trace;
    }

    private static StationExecutionContext stationContext(ExecutionContext globalContext) {
        return new DefaultStationExecutionContext("root", StationKind.PROCESSING, globalContext,
                StationLogTrace.start(globalContext.getExecutionId(), "root", null), null);
    }

    private static ExecutionContext executionContext() {
        return ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation() {
            super("root", StationKind.PROCESSING, null, null, null, false, null, null);
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
