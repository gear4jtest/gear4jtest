package io.github.gear4jtest.core.engine.strategies;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.SignalStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class SignalStationStrategy extends AbstractStationStrategy<SignalStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return SignalStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(SignalStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        var eligible = station.getCondition().test(new SignalStation.SignalInterpretationContext<>(input, operationExecution.getGlobalContext()));

        if (eligible) {
            switch (station.getSignalType()) {
                case FATAL -> operationExecution.getRecord().markFailed(null);
                case STOP -> operationExecution.getRecord().markStopped(null);
            }
        }
        return input;
    }
}
