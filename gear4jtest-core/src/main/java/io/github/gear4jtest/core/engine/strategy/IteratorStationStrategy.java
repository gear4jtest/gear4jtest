package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collector;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IteratorStationStrategy extends AbstractStationStrategy<IteratorStation<?, ?>> {
    @SuppressWarnings("unchecked")
    private static Collector<Object, Object, Object> resultCollector(Collector<?, ?, ?> collector) {
        return (Collector<Object, Object, Object>) collector;
    }

    @Override
    public boolean supports(Class<?> type) {
        return IteratorStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doExecute(IteratorStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());

        Object source;
        if (station.getFunc() != null) {
            source = ((Function<Object, ? extends Iterable<?>>) station.getFunc()).apply(input);
        } else {
            source = input;
        }
        if (!(source instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Iterator station '" + station.getId()
                    + "' requires a non-null Iterable source");
        }
        Iterator<?> iterator = iterable.iterator();
        if (iterator == null) {
            throw new IllegalStateException("Iterator station '" + station.getId() + "' returned a null iterator");
        }

        Collection<Object> results = null;
        Collector<Object, Object, Object> collector = null;
        Object collectorState = null;
        if (station.getAccumulator() != null) {
            results = station.getAccumulator().getCollectionSupplier().getSupplier().get();
        } else if (station.getCollector() != null) {
            collector = resultCollector(station.getCollector());
            collectorState = collector.supplier().get();
        } else {
            results = new ArrayList<>();
        }
        Throwable collectedError = null;

        long index = 0L;

        while (true) {
            if (operationExecution.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                EngineStationContexts.trace(operationExecution)
                        .markCancelled(operationExecution.getGlobalContext().getCancellationToken()
                                .cancellationCause().orElse(null));
                break;
            }
            if (!iterator.hasNext()) {
                break;
            }
            if (station.getMaxItems() != IteratorStation.UNLIMITED_ITEMS && index >= station.getMaxItems()) {
                throw new IllegalStateException("Iterator station '" + station.getId()
                        + "' exceeded its maximum item count of " + station.getMaxItems());
            }
            Object element = iterator.next();
            String itemId = (station.getItemIdResolver() != null)
                    ? station.getItemIdResolver().resolve(element, index, operationExecution.getGlobalContext())
                    : station.getId() + "#item-" + index;

            StationLogTrace chainResult;
            try (var ignored = operationExecution.getGlobalContext().enterItem(itemId)) {
                chainResult = EngineStationContexts.mutableTrace(
                                                                 runner.run(element, station.getChain(),
                                                                            operationExecution));
            }
            EngineStationContexts.trace(operationExecution).addSubOperation(chainResult);

            FlowDecision decision = FlowDecider.decide(chainResult, config);
            switch (decision) {
                case PROCEED -> {
                    if (chainResult.getStatus() == StationLogStatus.SUCCEEDED) {
                        if (collector != null) {
                            collector.accumulator().accept(collectorState, chainResult.getOutput());
                        } else {
                            results.add(chainResult.getOutput());
                        }
                    }
                }
                case MARK_AND_PROCEED -> {
                    if (collectedError == null) {
                        collectedError = FlowStrategySupport
                                .representativeThrowable(chainResult, "Item failed without exception: " + itemId);
                    }
                }
                case INTERRUPT ->
                    FlowStrategySupport.applyInterruptToParentLog(EngineStationContexts.trace(operationExecution),
                                                                  chainResult, config);
            }

            if (decision == FlowDecision.INTERRUPT) {
                break;
            }

            index++;
        }

        if (collectedError != null
                && EngineStationContexts.trace(operationExecution).getStatus() == StationLogStatus.RUNNING) {
            Throwable first = collectedError;
            EngineStationContexts.trace(operationExecution)
                    .markFailed(first instanceof Exception ex ? ex : new RuntimeException(first.getMessage(), first));
        }

        if (collector != null) {
            return collector.finisher().apply(collectorState);
        }

        return results;
    }
}
