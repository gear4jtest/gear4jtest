package io.github.gear4jtest.core.engine.strategies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.IteratorStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class IteratorStationStrategy extends AbstractStationStrategy<IteratorStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return IteratorStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(IteratorStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        Iterable<?> collection;
        if (station.getFunc() != null) {
            collection = ((Function<Object, ? extends Iterable<?>>) station.getFunc()).apply(input);
        } else {
            collection = (Iterable<?>) input;
        }

        Collection<Object> results = new ArrayList<>();

        long index = 0L;
        boolean success = true;

        for (Object element : collection) {
            String itemId = (station.getItemIdResolver() != null)
                    ? station.getItemIdResolver().resolve(element, index, operationExecution.getGlobalContext())
                    : station.getId() + "#item-" + index;

            StationLog chainResult =
                    operationExecution.getGlobalContext().withItemId(itemId, () -> runner.run(element, station.getChain(), operationExecution));

            // Rattache systématiquement chaque exécution enfant à l'iterator courant
//			operationExecution.getRecord().addSubOperation(rec);

            if (chainResult.getStatus() == StationLog.Status.FAILED || chainResult.getStatus() == StationLog.Status.STOPPED) {
//                success = false;
                operationExecution.getRecord().markFailed(null);
                break;
            }

            // Output fonctionnel pour la suite de la pipeline
            Object value = chainResult.getOutput(null);
            results.add(value);

//            if (!success) {
//                operationExecution.getRecord().markFailed(null);
//                break;
//            }
        }

        // Accumulateur / collector comme avant
        if (station.getAccumulator() != null) {
            Collection<Object> acc = station.getAccumulator().getCollectionSupplier().getSupplier().get();
            acc.addAll(results);
            return acc;
        }

        if (station.getCollector() != null) {
            return results.stream().collect(station.getCollector());
        }

        return results;
    }
}
