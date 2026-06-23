package io.github.gear4jtest.core.engine.runner;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationExceptionBoundaryRunnerTest {
    @Test
    void run_shouldReturnDelegateTraceWhenDelegateSucceeds() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext();
        context.getRecord().markSuccess("ok");
        StationRunner delegate = (input, currentStation, currentContext) -> currentContext.getRecord();
        StationExceptionBoundaryRunner runner = new StationExceptionBoundaryRunner(delegate,
                new StationErrorPolicyExecutor());

        StationLogTrace result = runner.run("input", station, context);

        assertThat(result).isSameAs(context.getRecord());
        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(result.<String>getOutput()).isEqualTo("ok");
    }

    @Test
    void run_shouldDelegateNormalizedExceptionToPolicyExecutor() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext();
        IllegalArgumentException original = new IllegalArgumentException("boom");
        StationRunner delegate = (input, currentStation, currentContext) -> {
            throw StationExecutionException.wrap(original);
        };
        StationErrorPolicyExecutor policy = new StationErrorPolicyExecutor() {
            @Override
            public StationLogTrace apply(AbstractStation<?, ?> currentStation,
                                         Object input,
                                         StationExecutionContext stationCtx,
                                         Exception exception) {
                assertThat(currentStation).isSameAs(station);
                assertThat(input).isEqualTo("input");
                assertThat(exception).isSameAs(original);
                stationCtx.getRecord().markSkipped(exception);
                return stationCtx.getRecord();
            }
        };
        StationExceptionBoundaryRunner runner = new StationExceptionBoundaryRunner(delegate, policy);

        StationLogTrace result = runner.run("input", station, context);

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(result.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void run_shouldFallbackToFailedStatusWhenPolicyFailsOnRunningLog() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext();
        IllegalStateException original = new IllegalStateException("delegate failed");
        RuntimeException policyFailure = new RuntimeException("policy failed");
        StationRunner delegate = (input, currentStation, currentContext) -> {
            throw original;
        };
        StationExceptionBoundaryRunner runner = new StationExceptionBoundaryRunner(delegate,
                failingPolicy(policyFailure));

        StationLogTrace result = runner.run("input", station, context);

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("delegate failed");
        assertThat(result.getErrorHandlerMessages()).contains("delegate failed", "policy failed");
        assertThat(result.getThrowables()).contains(original, policyFailure);
    }

    @Test
    void run_shouldPreserveExistingTerminalStatusWhenPolicyFails() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext();
        context.getRecord().markStopped(new RuntimeException("already stopped"));
        IllegalStateException original = new IllegalStateException("delegate failed");
        RuntimeException policyFailure = new RuntimeException("policy failed");
        StationRunner delegate = (input, currentStation, currentContext) -> {
            throw original;
        };
        StationExceptionBoundaryRunner runner = new StationExceptionBoundaryRunner(delegate,
                failingPolicy(policyFailure));

        StationLogTrace result = runner.run("input", station, context);

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.STOPPED);
        assertThat(result.getErrorMessage()).isEqualTo("already stopped");
        assertThat(result.getErrorHandlerMessages()).contains("already stopped", "delegate failed", "policy failed");
        assertThat(result.getThrowables()).contains(original, policyFailure);
    }

    private static StationErrorPolicyExecutor failingPolicy(RuntimeException failure) {
        return new StationErrorPolicyExecutor() {
            @Override
            public StationLogTrace apply(AbstractStation<?, ?> station,
                                         Object input,
                                         StationExecutionContext stationCtx,
                                         Exception exception) {
                throw failure;
            }
        };
    }

    private static StationExecutionContext stationContext() {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
        StationLogTrace trace = StationLogTrace.start(globalContext.getExecutionId(), "station", null);
        return new DefaultStationExecutionContext("station", StationKind.PROCESSING, globalContext, trace, null);
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation() {
            super("station", StationKind.PROCESSING, null, null, null, false, null, null);
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
