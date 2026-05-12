package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;

public final class RuntimeExtensionResolver {
    private static final Comparator<RuntimeExtension> ORDERING = Comparator.comparingInt(RuntimeExtension::getOrder)
            .thenComparing(ext -> ext.getClass().getName());
    private final List<RuntimeExtension> globalExtensions;

    public RuntimeExtensionResolver(List<RuntimeExtension> globalExtensions) {
        this.globalExtensions = globalExtensions == null ? List.of() : List.copyOf(globalExtensions);
    }

    private static <T extends RuntimeExtension> List<T> filter(List<RuntimeExtension> extensions, Class<T> type) {
        return extensions.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public ResolvedExtensions resolve(AssemblyLine<?, ?> pipeline, RunRequest request) {
        List<RuntimeExtension> merged = new ArrayList<>(globalExtensions);

        if (pipeline != null && pipeline.getConfiguration() != null
                && pipeline.getConfiguration().getDefaultExtensions() != null) {
            merged.addAll(pipeline.getConfiguration().getDefaultExtensions());
        }

        if (request != null && request.getExtensions() != null) {
            merged.addAll(request.getExtensions());
        }

        List<RuntimeExtension> ordered = merged.stream().sorted(ORDERING).toList();

        return new ResolvedExtensions(ordered, filter(ordered, RunInterceptorExtension.class),
                filter(ordered, RunLifecycleExtension.class), filter(ordered, StationWrapperExtension.class),
                filter(ordered, StationLifecycleExtension.class), filter(ordered, ExecutorWrapperExtension.class));
    }
}
