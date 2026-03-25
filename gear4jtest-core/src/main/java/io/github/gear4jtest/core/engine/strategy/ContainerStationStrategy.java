package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class ContainerStationStrategy extends AbstractStationStrategy<ContainerBaseStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return ContainerBaseStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(ContainerBaseStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        Collection<StationLog> results = new ArrayList<>();
        String currentItemId = operationExecution.getGlobalContext().getCurrentItemId();

        if (station.isParallel() && station.getExecutorService() != null) {
            List<Callable<StationLog>> tasks = new ArrayList<>();
            ExecutorService executor = operationExecution.getSupport().executorFor(station.getExecutorService(), operationExecution.getGlobalContext());

            for (ContainerBaseStation.Branch branch : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
                tasks.add(operationExecution.getSupport().getTaskFactory()
                        .createTask(() -> this.deepClone(input), branch.getStation(), runner, operationExecution, currentItemId));
            }

            try {
                List<Future<StationLog>> futures = executor.invokeAll(tasks);
                for (Future<StationLog> future : futures) {
                    StationLog value = future.get();
                    if (value != null) {
                        results.add(value);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Erreur dans une sous-ligne du container", e.getCause());
            }
        } else {
            for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
                Object newObject = deepClone(input);
                var rec = runner.run(newObject, element.getStation(), operationExecution);
                rec.setParentOperationId(operationExecution.getRecord().getId());

                if (rec.getStatus() == StationLog.Status.FAILED) {
                    operationExecution.getRecord().markFailed(null);
                    return null;
                }

                results.add(rec);
            }
        }

        return returns(station, results);
    }

    private Object returns(ContainerBaseStation station, Collection<StationLog> executions) {
        var returnedObjects = executions.stream().map(StationLog::getOutput).toArray();
        if (station.getFunc() != null) {
            return station.getFunc().apply(returnedObjects);
        } else {
            return null;
        }
    }

    <T> T deepClone(T object) {
        return object;
    }
}
