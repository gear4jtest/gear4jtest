package io.github.gear4jtest.core.engine.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationErrorPolicyExecutorTest {
    private final StationErrorPolicyExecutor executor = new StationErrorPolicyExecutor();

    @Test
    void apply_shouldMarkFailedWhenNoErrorPolicyMatches() {
        // Given
        RuntimeException failure = new RuntimeException("boom");
        TestStation station = new TestStation(List.of(), null, false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, failure);

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(stationLog.getErrorMessage()).isEqualTo("boom");
        assertThat(stationLog.getThrowables()).containsExactly(failure);
    }

    @Test
    void apply_shouldNotChangeTerminalRecordStatus() {
        // Given
        RuntimeException failure = new RuntimeException("late");
        TestStation station = new TestStation(List.of(ignore(RuntimeException.class)), null, false);
        StationExecutionContext context = stationContext("station");
        EngineStationContexts.trace(context).markSuccess("already-done");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, failure);

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(stationLog.<String>getOutput()).isEqualTo("already-done");
        assertThat(stationLog.getErrorHandlerMessages()).isEqualTo("late");
    }

    @Test
    void apply_shouldIgnoreNullPoliciesAndNonMatchingTypesOrConditions() {
        // Given
        RuntimeException failure = new RuntimeException("boom");
        List<BaseError<String>> policies = new ArrayList<>();
        policies.add(null);
        policies.add(ignore(IllegalStateException.class));
        policies.add(new BaseError.UnSafeError.Builder<String>(SignalType.IGNORE, RuntimeException.class)
                .condition((input, ctx) -> false)
                .build());
        TestStation station = new TestStation(policies, null, false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, failure);

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(stationLog.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void apply_shouldRunMatchingActionAndFallbackForIgnorePolicy() {
        // Given
        AtomicInteger actions = new AtomicInteger();
        RuntimeException failure = new RuntimeException("boom");
        BaseError<String> policy = new BaseError.UnSafeError.Builder<String>(SignalType.IGNORE,
                RuntimeException.class)
                .condition((input, ctx) -> input.equals("input"))
                .action(actions::incrementAndGet)
                .build();
        TestStation station = new TestStation(List.of(policy), (input, ctx) -> input + "-fallback", false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, failure);

        // Then
        assertThat(actions).hasValue(1);
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(stationLog.<String>getOutput()).isEqualTo("input-fallback");
    }

    @Test
    void apply_shouldKeepIgnorePolicyWhenActionFails() {
        // Given
        RuntimeException actionFailure = new RuntimeException("handler failed");
        BaseError<String> policy = new BaseError.UnSafeError.Builder<String>(SignalType.IGNORE,
                RuntimeException.class)
                .action(() -> {
                    throw actionFailure;
                })
                .build();
        TestStation station = new TestStation(List.of(policy), (input, ctx) -> input + "-fallback", false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, new RuntimeException("boom"));

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(stationLog.<String>getOutput()).isEqualTo("input-fallback");
        assertThat(stationLog.getErrorHandlerMessages()).contains("handler failed");
        assertThat(stationLog.getThrowables()).contains(actionFailure);
    }

    @Test
    void apply_shouldMarkFailedWhenIgnoreFallbackFails() {
        // Given
        RuntimeException original = new RuntimeException("boom");
        RuntimeException fallbackFailure = new RuntimeException("fallback failed");
        TestStation station = new TestStation(List.of(ignore(RuntimeException.class)), (input, ctx) -> {
            throw fallbackFailure;
        }, false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, original);

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(stationLog.getErrorMessage()).isEqualTo("fallback failed");
        assertThat(stationLog.getErrorHandlerMessages()).contains("fallback failed").contains("boom");
    }

    @Test
    void apply_shouldCarryInputForwardWhenUnaryStationIgnoresWithoutFallback() {
        // Given
        TestStation station = new TestStation(List.of(ignore(RuntimeException.class)), null, true);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, new RuntimeException("boom"));

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(stationLog.<String>getOutput()).isEqualTo("input");
    }

    @Test
    void apply_shouldSkipNonUnaryStationWhenIgnoreHasNoFallback() {
        // Given
        TestStation station = new TestStation(List.of(ignore(RuntimeException.class)), null, false);
        StationExecutionContext context = stationContext("station");

        // When
        StationLogTrace stationLog = executor.apply(station, "input", context, new RuntimeException("boom"));

        // Then
        assertThat(stationLog.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(stationLog.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void apply_shouldMapStopAndFatalPoliciesToTerminalStatuses() {
        // Given
        RuntimeException failure = new RuntimeException("boom");
        TestStation stopStation = new TestStation(List.of(safe(SignalType.STOP, RuntimeException.class)), null, false);
        TestStation fatalStation = new TestStation(List.of(safe(SignalType.FATAL, RuntimeException.class)), null,
                false);

        // When
        StationLogTrace stopped = executor.apply(stopStation, "input", stationContext("stop"), failure);
        StationLogTrace failed = executor.apply(fatalStation, "input", stationContext("fatal"), failure);

        // Then
        assertThat(stopped.getStatus()).isEqualTo(StationLogStatus.STOPPED);
        assertThat(stopped.getErrorMessage()).isEqualTo("boom");
        assertThat(failed.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("boom");
    }

    private static BaseError<String> ignore(Class<? extends Throwable> throwableType) {
        return new BaseError.UnSafeError.Builder<String>(SignalType.IGNORE, throwableType).build();
    }

    private static BaseError<String> safe(SignalType signalType, Class<? extends Throwable> throwableType) {
        return new BaseError.SafeError.Builder<String>(signalType, throwableType).build();
    }

    private static StationExecutionContext stationContext(String operationId) {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipeline", Map.of()))
                .build();
        StationLogTrace stationLog = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.PROCESSING, globalContext, stationLog, null);
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation(List<BaseError<String>> onErrors,
                            Operator<String, String> fallbackOperator,
                            boolean unary) {
            super("station", StationKind.PROCESSING, null, onErrors, fallbackOperator, unary, null, null);
        }
    }
}
