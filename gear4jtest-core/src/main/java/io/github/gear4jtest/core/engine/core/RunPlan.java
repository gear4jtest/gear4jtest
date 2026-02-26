package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.ExecutorWrapperExtension;
import io.github.gear4jtest.core.engine.spi.RunInterceptorExtension;
import io.github.gear4jtest.core.engine.spi.StationWrapperExtension;
import java.util.List;

public record RunPlan(
    List<RunInterceptorExtension> runInterceptors,
    List<StationWrapperExtension> stationWrappers,
    List<ExecutorWrapperExtension> executorWrappers
) {}
