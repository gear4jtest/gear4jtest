package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalStationStrategyTest {
    private final SignalStationStrategy strategy = new SignalStationStrategy();

    @Test
    void supports_shouldMatchSignalStationsOnly() {
        assertThat(strategy.supports(stationType(SignalStation.class))).isTrue();
        assertThat(strategy.supports(stationType(WorkStation.class))).isFalse();
    }

    @Test
    void doExecute_shouldMarkFailedWhenFatalSignalIsEligible() {
        StationExecutionContext context = stationContext("fatal");
        Object output = strategy.doExecute(signal(SignalType.FATAL, true), "input", null, context);

        assertThat(output).isEqualTo("input");
        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.FAILED);
    }

    @Test
    void doExecute_shouldMarkStoppedWhenStopSignalIsEligible() {
        StationExecutionContext context = stationContext("stop");

        strategy.doExecute(signal(SignalType.STOP, true), "input", null, context);

        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.STOPPED);
    }

    @Test
    void doExecute_shouldLeaveStatusRunningWhenSignalIsNotEligible() {
        StationExecutionContext context = stationContext("ineligible");

        strategy.doExecute(signal(SignalType.FATAL, false), "input", null, context);

        assertThat(context.getRecord().getStatus()).isEqualTo(StationLogStatus.RUNNING);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractStation<?, ?>> stationType(Class<?> type) {
        return (Class<? extends AbstractStation<?, ?>>) type;
    }

    private static SignalStation<String> signal(SignalType signalType, boolean eligible) {
        return new SignalStation.Builder<String>()
                .id("signal")
                .type(signalType)
                .condition(ctx -> eligible && "input".equals(ctx.getItem()))
                .build();
    }

    private static StationExecutionContext stationContext(String operationId) {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
        StationLogTrace trace = StationLogTrace.start(globalContext.getExecutionId(), operationId, null);
        return new DefaultStationExecutionContext(operationId, StationKind.SIGNAL, globalContext, trace, null);
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
