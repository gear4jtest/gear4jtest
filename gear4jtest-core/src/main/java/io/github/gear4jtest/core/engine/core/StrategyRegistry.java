package io.github.gear4jtest.core.engine.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.engine.strategies.ContainerStationStrategy;
import io.github.gear4jtest.core.engine.strategies.IfElseContainerStationStrategy;
import io.github.gear4jtest.core.engine.strategies.IteratorStationStrategy;
import io.github.gear4jtest.core.engine.strategies.SequenceStationStrategy;
import io.github.gear4jtest.core.engine.strategies.SignalStationStrategy;
import io.github.gear4jtest.core.engine.strategies.StationExecutionStrategy;
import io.github.gear4jtest.core.engine.strategies.WorkStationStrategy;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.IteratorStation;
import io.github.gear4jtest.core.model.Station;

public class StrategyRegistry {

    private final Map<Class<? extends AbstractStation>, StationExecutionStrategy<?>> cache = new ConcurrentHashMap<>();
    private final List<StationExecutionStrategy<?>> strategies;

    public static StrategyRegistry defaultRegistry() {
        return new StrategyRegistry(List.of(
                new WorkStationStrategy(),
                new SequenceStationStrategy<>(),
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
    public <S extends AbstractStation> StationExecutionStrategy<S> getStrategy(S station) {
        return (StationExecutionStrategy<S>) cache.computeIfAbsent(station.getClass(), type -> 
            strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No strategy for " + type))
        );
    }
}