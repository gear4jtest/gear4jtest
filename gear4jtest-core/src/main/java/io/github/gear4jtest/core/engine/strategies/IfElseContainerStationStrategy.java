package io.github.gear4jtest.core.engine.strategies;

import java.util.List;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ContainerBaseStation;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.persistence.StationLog;

public class IfElseContainerStationStrategy extends AbstractStationStrategy<UnaryIfElseContainerStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return UnaryIfElseContainerStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(UnaryIfElseContainerStation station, Object input, ExecutionContext context, StationRunner runner, StationExecutionContext operationExecution) {
        boolean conditionMet = false;
        Object containerResult = null;

        for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (element.getCondition() == null || element.getCondition().test(input, context)) {
                conditionMet = true;
                Object newObject = deepClone(input);
                var rec = runner.run(newObject, element.getStation(), operationExecution);
//                var rec = element.getStation().run(newObject, context);
                rec.setParentOperationId(operationExecution.getOperationId());
//                context.getExecutionManager().append(rec);
                if (rec.getStatus() == StationLog.Status.FAILED) {
                    operationExecution.getRecord().markFailed(null);
                    return null;
                }

                containerResult = rec.getOutput(Object.class);
                break;
            }
        }

        if (!conditionMet && station.getElseOp() != null) {
            Object newObject = deepClone(input);
            var recElse = runner.run(newObject, station.getElseOp(), operationExecution);
//            var recElse = station.getElseOp().run(newObject, context);
            recElse.setParentOperationId(operationExecution.getOperationId());
//            context.getExecutionManager().append(recElse);
            if (recElse.getStatus() == StationLog.Status.FAILED) {
                operationExecution.getRecord().markFailed(null);
                return null;
            }
            containerResult = recElse.getOutput(Object.class);
        }

        return containerResult;
    }

    <T> T deepClone(T object) {
        return object;
    }
}
