package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.model.ExecutionContext;

public interface StationWrapperExtension extends RuntimeExtension {
    StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx);
}
