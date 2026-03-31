package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IteratorStationStrategy extends AbstractStationStrategy<IteratorStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return IteratorStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(IteratorStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        Iterable<?> collection;
        if (station.getFunc() != null) {
            collection = ((Function<Object, ? extends Iterable<?>>) station.getFunc()).apply(input);
        } else {
            collection = (Iterable<?>) input;
        }

        Collection<Object> results = new ArrayList<>();
        Collection<Throwable> collectedErrors = new ArrayList<>();

        long index = 0L;

        for (Object element : collection) {
            String itemId = (station.getItemIdResolver() != null)
                    ? station.getItemIdResolver().resolve(element, index, operationExecution.getGlobalContext())
                    : station.getId() + "#item-" + index;

            StationLog chainResult = runner.run(element, station.getChain(), operationExecution);

            // Rattache systématiquement chaque exécution enfant à l'iterator courant
//			operationExecution.getRecord().addSubOperation(rec);

            FlowDecision decision = FlowDecider.decide(chainResult, config);
            switch (decision) {
                case PROCEED -> {
                    // On ne produit un output que si la chain a réussi.
                    if (chainResult.getStatus() == StationLog.Status.SUCCEEDED) {
                        results.add(chainResult.getOutput());
                    }
                }
                case MARK_AND_PROCEED -> collectedErrors.add(
                        FlowStrategySupport.representativeThrowable(
                                chainResult,
                                "Item failed without exception: " + itemId));
                case INTERRUPT -> FlowStrategySupport.applyInterruptToParentLog(
                        operationExecution.getRecord(),
                        chainResult,
                        config);
            }

            if (decision == FlowDecision.INTERRUPT) {
                break;
            }

            index++;

//            if (!success) {
//                operationExecution.getRecord().markFailed(null);
//                break;
//            }
        }

        // Fin : si erreurs collectées => échec global
        if (!collectedErrors.isEmpty()) {
            Throwable first = collectedErrors.iterator().next();
            operationExecution.getRecord().markFailed(
                    first instanceof Exception ex ? ex : new RuntimeException(first.getMessage(), first));
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
