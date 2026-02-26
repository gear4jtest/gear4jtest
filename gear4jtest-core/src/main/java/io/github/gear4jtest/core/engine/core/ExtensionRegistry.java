package io.github.gear4jtest.core.engine.core;

import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.engine.spi.ExecutorWrapperExtension;
import io.github.gear4jtest.core.engine.spi.RunInterceptorExtension;
import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.engine.spi.StationWrapperExtension;

public class ExtensionRegistry {

    private static final Comparator<RuntimeExtension> ORDERING =
            Comparator.comparingInt(RuntimeExtension::getOrder)
                    .thenComparing(ext -> ext.getClass().getName());

    private final List<RuntimeExtension> allExtensions;
    private final List<RunInterceptorExtension> runInterceptors;
    private final List<StationWrapperExtension> stationWrappers;
    private final List<ExecutorWrapperExtension> executorWrappers;

    public ExtensionRegistry(List<RuntimeExtension> extensions) {
        this.allExtensions = (extensions == null) ? List.of() : List.copyOf(extensions);
        this.runInterceptors = getExtensionOfType(RunInterceptorExtension.class);
        this.stationWrappers = getExtensionOfType(StationWrapperExtension.class);
        this.executorWrappers = getExtensionOfType(ExecutorWrapperExtension.class);
    }

    public List<RuntimeExtension> getAllExtensions() {
        return allExtensions;
    }

    public List<RunInterceptorExtension> getRunInterceptors() {
        return runInterceptors;
    }

    public List<StationWrapperExtension> getStationWrappers() {
        return stationWrappers;
    }

    public List<ExecutorWrapperExtension> getExecutorWrappers() {
        return executorWrappers;
    }

    private <T extends RuntimeExtension> List<T> getExtensionOfType(Class<T> clazz) {
        return this.allExtensions.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .sorted(ORDERING)
                .toList();
    }

//    public List<RuntimeExtension> find(List<String> requestedFeatures) {
//        List<RuntimeExtension> found = new ArrayList<>();
//        if (requestedFeatures == null) return found;
//
//        for (String feature : requestedFeatures) {
//            if (extensions.containsKey(feature)) {
//                found.add(extensions.get(feature));
//            } else {
//                // On peut décider de logger un warning ou d'ignorer
//            }
//        }
//        return found;
//    }
}
