package io.github.gear4jtest.core.engine.runner;

import java.util.Objects;

import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class RecursiveStationRunner implements StationRunner {

    private final StationRunner rootRunner;

    public RecursiveStationRunner(StationRunner rootRunner) {
        this.rootRunner = Objects.requireNonNull(rootRunner, "rootRunner must not be null");
    }

    @Override
    public StationLog run(Object input, AbstractStation station, StationExecutionContext ctx) {
        return rootRunner.run(input, station, ctx);
    }
}
