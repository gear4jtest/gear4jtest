package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public interface StationWrapperExtension extends RuntimeExtension {
    StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx);
}
