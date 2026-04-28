package io.github.gear4jtest.core.engine.runner;

import java.util.Objects;

import io.github.gear4jtest.core.engine.strategy.StationExecutionStrategy;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

public class TerminalStationRunner implements StationRunner {

    private final StrategyRegistry registry;
    private final StationRunner recursiveRunner;

    public TerminalStationRunner(StrategyRegistry registry, StationRunner recursiveRunner) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.recursiveRunner = Objects.requireNonNull(recursiveRunner, "recursiveRunner must not be null");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx) {
        StationExecutionStrategy<AbstractStation<?, ?>> strategy =
                (StationExecutionStrategy) registry.getStrategy(station);
        return strategy.run(station, input, ctx, recursiveRunner);
    }
}
