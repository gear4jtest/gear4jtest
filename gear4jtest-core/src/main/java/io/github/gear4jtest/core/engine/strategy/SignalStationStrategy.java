package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class SignalStationStrategy extends AbstractStationStrategy<SignalStation<?>> {
    @SuppressWarnings("unchecked")
    private static <I> boolean evaluateCondition(SignalStation<I> station, Object input, StationExecutionContext ctx) {
        return station.getCondition()
                .test(new SignalStation.SignalInterpretationContext<>((I) input, ctx.getGlobalContext()));
    }

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return SignalStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(SignalStation<?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        boolean eligible = evaluateCondition(station, input, operationExecution);

        if (eligible) {
            switch (station.getSignalType()) {
                case FATAL -> operationExecution.getRecord().markFailed(null);
                case STOP -> operationExecution.getRecord().markStopped(null);
                case IGNORE -> throw new IllegalStateException("SignalStation cannot emit IGNORE");
            }
        }
        return input;
    }
}
