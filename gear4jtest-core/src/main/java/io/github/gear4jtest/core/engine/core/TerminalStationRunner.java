package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class TerminalStationRunner implements StationRunner {

    private final StrategyRegistry registry;
    private StationRunner rootRunner;

    public TerminalStationRunner(StrategyRegistry registry) {
        this.registry = registry;
        this.rootRunner = this;
    }

    public void setRootRunner(StationRunner rootRunner) {
        this.rootRunner = rootRunner;
    }

    @Override
    public StationLog run(Object input, AbstractStation station, StationExecutionContext ctx) {
        var strategy = registry.getStrategy(station);
        return strategy.run(station, input, ctx, this.rootRunner);
    }
}