package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collector;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IteratorStationStrategy extends AbstractStationStrategy<IteratorStation<?, ?>> {
    @SuppressWarnings("unchecked")
    private static <A, R> R collectResults(Collection<Object> results, Collector<?, A, R> collector) {
        return results.stream()
                .collect((Collector<? super Object, A, R>) collector);
    }

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return IteratorStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doExecute(IteratorStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
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

            StationLogTrace chainResult;
            try (var ignored = operationExecution.getGlobalContext().enterItem(itemId)) {
                chainResult = EngineStationContexts.mutableTrace(
                                                                 runner.run(element, station.getChain(),
                                                                            operationExecution));
            }

            FlowDecision decision = FlowDecider.decide(chainResult, config);
            switch (decision) {
                case PROCEED -> {
                    if (chainResult.getStatus() == StationLogStatus.SUCCEEDED) {
                        results.add(chainResult.getOutput());
                    }
                }
                case MARK_AND_PROCEED -> collectedErrors.add(FlowStrategySupport
                        .representativeThrowable(chainResult, "Item failed without exception: " + itemId));
                case INTERRUPT ->
                    FlowStrategySupport.applyInterruptToParentLog(EngineStationContexts.trace(operationExecution),
                                                                  chainResult, config);
            }

            if (decision == FlowDecision.INTERRUPT) {
                break;
            }

            index++;
        }

        if (!collectedErrors.isEmpty()) {
            Throwable first = collectedErrors.iterator().next();
            EngineStationContexts.trace(operationExecution)
                    .markFailed(first instanceof Exception ex ? ex : new RuntimeException(first.getMessage(), first));
        }

        if (station.getAccumulator() != null) {
            Collection<Object> acc = station.getAccumulator().getCollectionSupplier().getSupplier().get();
            acc.addAll(results);
            return acc;
        }

        if (station.getCollector() != null) {
            return collectResults(results, station.getCollector());
        }

        return results;
    }
}
