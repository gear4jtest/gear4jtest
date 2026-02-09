package io.github.gear4jtest.core.engine.strategies;

import java.util.List;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.SequenceStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class SequenceStationStrategy extends AbstractStationStrategy<SequenceStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return SequenceStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doExecute(SequenceStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        StationLog rec = null;
        Object in = input;
        boolean success = true;

        for (AbstractStation<?, ?> step :  (List<AbstractStation>) station.getSteps()) {
//            Station<Object, Object> typed = (Station<Object, Object>) step;
            rec = runner.run(in, step, operationExecution);
//            ctx.getExecutionManager().append(rec);

            if (rec.getStatus() == StationLog.Status.FAILED || rec.getStatus() == StationLog.Status.STOPPED) {
                success = false;
                break;
            }

            in = rec.getOutput(Object.class);
        }
        return in;
    }
}
