package io.github.gear4jtest.core.engine.strategies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ContainerBaseStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class ContainerStationStrategy extends AbstractStationStrategy<ContainerBaseStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return ContainerBaseStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(ContainerBaseStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        Collection<Object> results = new ArrayList<>();
        String currentItemId = operationExecution.getGlobalContext().getCurrentItemId();

        if (station.isParallel() && station.getExecutorService() != null) {
            List<Callable<Object>> tasks = new ArrayList<>();

            for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
                tasks.add(() -> operationExecution.getGlobalContext().withItemId(currentItemId, () -> {
                    Object newObject = deepClone(input);
                    var rec = runner.run(newObject, element.getStation(), operationExecution);
//                    var rec = element.getStation().run(newObject, context);

                    rec.setParentOperationId(operationExecution.getOperationId());
//					context.getExecutionManager().append(rec);

                    if (rec.getStatus() == StationLog.Status.FAILED) {
                        // On marque l'opération globale en échec
                        operationExecution.getRecord().markFailed(null);
                        return null; // pas de résultat pour cette branche
                    }

                    return rec.getOutput(Object.class);
                }));
            }

            try {
                // Lance toutes les tâches et attend qu’elles soient terminées
                List<Future<Object>> futures = station.getExecutorService().invokeAll(tasks);

                for (Future<Object> future : futures) {
                    Object value = future.get(); // bloque jusqu'à fin de la tâche
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

            // ⚠ À toi de décider si on shutdown ici ou non
            // Si l'executor est fourni de l'extérieur (comme dans ton test),
            // je te conseille de NE PAS le shutdown dans le container.
            // executorService.shutdown();

        } else {
            // Version séquentielle inchangée
            for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
                Object newObject = deepClone(input);
                var rec = runner.run(newObject, element.getStation(), operationExecution);
                rec.setParentOperationId(operationExecution.getOperationId());
//				context.getExecutionManager().append(rec);

                if (rec.getStatus() == StationLog.Status.FAILED) {
                    operationExecution.getRecord().markFailed(null);
                    return null;
                }

                results.add(rec.getOutput(Object.class));
            }
        }

        return returns(station, results);
    }

    private Object returns(ContainerBaseStation station, Collection<Object> executions) {
        var returnedObjects = executions.toArray();
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
