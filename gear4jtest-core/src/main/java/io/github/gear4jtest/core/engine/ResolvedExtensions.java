package io.github.gear4jtest.core.engine;

import java.util.List;

import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;

public record ResolvedExtensions(
        List<RuntimeExtension> allExtensions,
        List<RunInterceptorExtension> runInterceptors,
        List<RunLifecycleExtension> runLifecycleExtensions,
        List<StationWrapperExtension> stationWrappers,
        List<StationLifecycleExtension> stationLifecycleExtensions,
        List<ExecutorWrapperExtension> executorWrappers) {}
