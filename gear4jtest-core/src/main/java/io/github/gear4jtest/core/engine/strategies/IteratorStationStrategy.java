package io.github.gear4jtest.core.engine.strategies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

import io.github.gear4jtest.core.engine.flow.CancelPolicy;
import io.github.gear4jtest.core.engine.flow.FlowConfig;
import io.github.gear4jtest.core.engine.flow.FlowDecider;
import io.github.gear4jtest.core.engine.flow.FlowDecision;
import io.github.gear4jtest.core.engine.flow.StopPolicy;
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
        FlowConfig config = station.getFlowConfig() != null ? station.getFlowConfig() : FlowConfig.DEFAULT;
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

            StationLog chainResult =
                    operationExecution.getGlobalContext().withItemId(itemId, () -> runner.run(element, station.getChain(), operationExecution));

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
                case MARK_AND_PROCEED -> {
                    if (chainResult.getThrowables() != null && !chainResult.getThrowables().isEmpty()) {
                        collectedErrors.addAll(chainResult.getThrowables());
                    } else {
                        collectedErrors.add(new RuntimeException("Item failed without exception: " + itemId));
                    }
                }
                case INTERRUPT -> applyInterruptToParentLog(operationExecution.getRecord(), chainResult, config);
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

    private static void applyInterruptToParentLog(StationLog parent, StationLog child, FlowConfig config) {
        StationLog.Status childStatus = child.getStatus();
        Exception representative = null;
        if (child.getThrowables() != null && !child.getThrowables().isEmpty()) {
            Throwable t = child.getThrowables().get(0);
            representative = (t instanceof Exception ex) ? ex : new RuntimeException(t.getMessage(), t);
        } else if (child.getErrorMessage() != null) {
            representative = new RuntimeException(child.getErrorMessage());
        }

        if (childStatus == StationLog.Status.FAILED) {
            parent.markFailed(representative);
            return;
        }

        if (childStatus == StationLog.Status.STOPPED) {
            if (config.stopPolicy() == StopPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markStopped(representative);
            }
            return;
        }

        if (childStatus == StationLog.Status.CANCELLED) {
            if (config.cancelPolicy() == CancelPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markCancelled(representative);
            }
            return;
        }

        parent.markFailed(representative != null ? representative : new RuntimeException("Unknown terminal status: " + childStatus));
    }
}
