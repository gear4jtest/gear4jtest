package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.station.AbstractStation;

public class StrategyRegistry {
    private final Map<Class<? extends AbstractStation<?, ?>>, StationExecutionStrategy<?>> cache = new ConcurrentHashMap<>();
    private final List<StationExecutionStrategy<?>> strategies;

    public StrategyRegistry(List<StationExecutionStrategy<?>> strategies) {
        this.strategies = strategies;
    }

    /**
     * Creates the default registry with nested pipeline execution disabled.
     *
     * <p>
     * This overload is kept for existing tests/custom engines. A
     * {@code PipelineCallStation} running in {@code NESTED_RUN} mode requires the
     * overload accepting a {@link NestedPipelineExecutor}.
     * </p>
     */
    public static StrategyRegistry defaultRegistry() {
        return defaultRegistry(NestedPipelineExecutor.unsupported());
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor) {
        Objects.requireNonNull(nestedPipelineExecutor, "nestedPipelineExecutor must not be null");
        return new StrategyRegistry(
                List.of(new WorkStationStrategy(), new SequenceStationStrategy(), new IteratorStationStrategy(),
                        new IfElseContainerStationStrategy(), new ContainerStationStrategy(),
                        new SignalStationStrategy(), new PipelineCallStationStrategy(nestedPipelineExecutor)));
    }

    @SuppressWarnings("unchecked")
    public <S extends AbstractStation<?, ?>> StationExecutionStrategy<S> getStrategy(S station) {
        return (StationExecutionStrategy<S>) cache
                .computeIfAbsent((Class<? extends AbstractStation<?, ?>>) station.getClass(),
                                 type -> strategies.stream()
                                         .filter(s -> s.supports(type))
                                         .findFirst()
                                         .orElseThrow(() -> new IllegalStateException("No strategy for " + type)));
    }
}
