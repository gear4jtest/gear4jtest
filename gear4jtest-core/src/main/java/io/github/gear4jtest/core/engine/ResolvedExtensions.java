package io.github.gear4jtest.core.engine;

import java.util.List;

import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;

public record ResolvedExtensions(List<RuntimeExtension> allExtensions,
                                 List<RunInterceptorExtension> runInterceptors,
                                 List<StationWrapperExtension> stationWrappers,
                                 List<ExecutorWrapperExtension> executorWrappers) {}
