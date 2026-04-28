package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.station.AbstractStation;

public class StrategyRegistry {

    private final Map<Class<? extends AbstractStation<?, ?>>, StationExecutionStrategy<?>> cache = new ConcurrentHashMap<>();
    private final List<StationExecutionStrategy<?>> strategies;

    public static StrategyRegistry defaultRegistry() {
        return new StrategyRegistry(List.of(
                new WorkStationStrategy(),
                new SequenceStationStrategy(),
                new IteratorStationStrategy(),
                new IfElseContainerStationStrategy(),
                new ContainerStationStrategy(),
                new SignalStationStrategy()
        ));
    }

    public StrategyRegistry(List<StationExecutionStrategy<?>> strategies) {
        this.strategies = strategies;
    }

    @SuppressWarnings("unchecked")
    public <S extends AbstractStation<?, ?>> StationExecutionStrategy<S> getStrategy(S station) {
        return (StationExecutionStrategy<S>) cache.computeIfAbsent(
                (Class<? extends AbstractStation<?, ?>>) station.getClass(),
                type -> strategies.stream()
                        .filter(s -> s.supports(type))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No strategy for " + type)));
    }
}
