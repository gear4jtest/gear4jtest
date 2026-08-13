package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractStationStrategyTest {
    @Test
    void run_shouldMarkCancelledWithoutExecutingStationWhenCancellationWasRequested() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext("cancelled");
        context.getGlobalContext().getCancellationToken().cancel("stop requested");
        TestStrategy strategy = new TestStrategy();

        StationLogTrace result = strategy.run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.CANCELLED);
        assertThat(result.getErrorMessage()).isEqualTo("stop requested");
        assertThat(strategy.executed).isFalse();
    }

    @Test
    void run_shouldApplyFallbackWhenPreProcessorSkipperMatches() {
        TestStation station = new TestStation(List.of(), (input, ctx) -> input + "-fallback",
                List.of(StationSkipper.pre((input, ctx) -> true, "not today")));
        StationExecutionContext context = stationContext("skipped");
        TestStrategy strategy = new TestStrategy();

        StationLogTrace result = strategy.run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
        assertThat(result.getOutput()).isEqualTo("input-fallback");
        assertThat(result.getContext()).containsEntry("skip.reason", "not today");
        assertThat(strategy.executed).isFalse();
    }

    @Test
    void run_shouldMarkFailedWhenSkipFallbackFails() {
        TestStation station = new TestStation(List.of(), (input, ctx) -> {
            throw new IllegalStateException("fallback failed");
        }, List.of(StationSkipper.pre((input, ctx) -> true, "not today")));
        StationExecutionContext context = stationContext("skipped");
        TestStrategy strategy = new TestStrategy();

        StationLogTrace result = strategy.run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("fallback failed");
        assertThat(result.getErrorHandlerMessages()).contains("fallback failed");
        assertThat(strategy.executed).isFalse();
    }

    @Test
    void run_shouldContinueWhenProcessorFailureModeAllowsItAndRecordError() {
        TestStation station = new TestStation(List.of(new Processor() {
            @Override
            public <I> void beforeExecution(I input, StationExecutionContext ctx) {
                throw new IllegalStateException("before ignored");
            }

            @Override
            public void afterExecution(Object result, StationExecutionContext context) {
                throw new IllegalStateException("after ignored");
            }
        }), null, List.of());
        StationExecutionContext context = stationContext("processor");

        StationLogTrace result = new TestStrategy().run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(result.getOutput()).isEqualTo("input-out");
        assertThat(result.getErrorHandlerMessages()).contains("before ignored", "after ignored");
    }

    @Test
    void run_shouldWrapProcessorFailureWhenFailureModeFailsStation() {
        TestStation station = new TestStation(List.of(new Processor() {
            @Override
            public <I> void beforeExecution(I input, StationExecutionContext ctx) {
                throw new IllegalStateException("before fatal");
            }

            @Override
            public void afterExecution(Object result, StationExecutionContext context) {
            }

            @Override
            public FailureMode beforeExecutionFailureMode() {
                return FailureMode.FAIL_STATION;
            }
        }), null, List.of());
        StationExecutionContext context = stationContext("processor-fatal");

        assertThatThrownBy(() -> new TestStrategy().run(station, "input", context, noopRunner()))
                .isInstanceOf(StationExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("before fatal");
    }

    @Test
    void run_shouldKeepExplicitTerminalStatusAndSetOutputWhenStrategyStopsStation() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext("stopped");
        TestStrategy strategy = new TestStrategy();
        strategy.stopDuringExecute = true;

        StationLogTrace result = strategy.run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.STOPPED);
        assertThat(result.getOutput()).isEqualTo("input-out");
        assertThat(result.getEndedAt()).isNotNull();
    }

    @Test
    void run_shouldRecordReleaseFailuresWithoutReplacingSuccessfulResult() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext("release");
        TestStrategy strategy = new TestStrategy();
        strategy.releaseFailure = new IllegalStateException("release failed");

        StationLogTrace result = strategy.run(station, "input", context, noopRunner());

        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(result.getOutput()).isEqualTo("input-out");
        assertThat(result.getErrorHandlerMessages()).contains("release failed");
    }

    @Test
    void run_shouldPassMainExceptionToReleaseWhenExecutionFailsBeforeRecordContainsIt() {
        TestStation station = new TestStation();
        StationExecutionContext context = stationContext("main-failure");
        TestStrategy strategy = new TestStrategy();
        strategy.executionFailure = new IllegalStateException("execute failed");
        AtomicReference<List<Throwable>> releaseErrors = new AtomicReference<>();
        strategy.releaseObserver = releaseErrors::set;

        assertThatThrownBy(() -> strategy.run(station, "input", context, noopRunner()))
                .isInstanceOf(StationExecutionException.class)
                .hasRootCauseMessage("execute failed");

        assertThat(releaseErrors.get()).extracting(Throwable::getMessage).containsExactly("execute failed");
    }

    private static StationRunner noopRunner() {
        return (input, station, ctx) -> ctx.getRecord();
    }

    private static StationExecutionContext stationContext(String operationId) {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
        StationLogTrace trace = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.PROCESSING, globalContext, trace, null);
    }

    private static final class TestStrategy extends AbstractStationStrategy<TestStation> {
        private boolean executed;
        private boolean stopDuringExecute;
        private RuntimeException executionFailure;
        private RuntimeException releaseFailure;
        private java.util.function.Consumer<List<Throwable>> releaseObserver;

        @Override
        public boolean supports(Class<?> type) {
            return TestStation.class.isAssignableFrom(type);
        }

        @Override
        protected Object doExecute(TestStation station,
                                   Object input,
                                   StationRunner runner,
                                   StationExecutionContext opContext) {
            executed = true;
            if (executionFailure != null) {
                throw executionFailure;
            }
            if (stopDuringExecute) {
                EngineStationContexts.trace(opContext).markStopped(null);
            }
            return input + "-out";
        }

        @Override
        protected void release(TestStation station,
                               Object result,
                               StationExecutionContext context,
                               List<Throwable> errors) {
            if (releaseObserver != null) {
                releaseObserver.accept(errors);
            }
            if (releaseFailure != null) {
                throw releaseFailure;
            }
        }
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation() {
            this(List.of(), null, List.of());
        }

        private TestStation(List<Processor> processors,
                            Operator<String, String> fallback,
                            List<StationSkipper> skippers) {
            super("station", StationKind.PROCESSING, processors, null, fallback, false, skippers, null);
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
