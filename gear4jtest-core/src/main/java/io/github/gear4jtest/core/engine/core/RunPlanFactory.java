package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RunPlanFactory {

    private static final Comparator<RuntimeExtension> ORDERING =
        Comparator.comparingInt(RuntimeExtension::getOrder)
                  .thenComparing(ext -> ext.getClass().getName());

    private RunPlanFactory() {}

    public static RunPlan create(ExtensionRegistry globalRegistry, RunRequest request) {
        
        // 1. On récupère les extensions globales
        List<RuntimeExtension> activeExtensions = new ArrayList<>(globalRegistry.getAllExtensions());
        
        // 2. [OPTIONNEL] On ajoute les extensions spécifiques à ce run si RunRequest le permet
        // if (request.getExtensions() != null) {
        //     activeExtensions.addAll(request.getExtensions());
        // }

        // 3. Filtrage et tri (Fait une seule fois par Run)
        List<RunInterceptorExtension> runInterceptors = activeExtensions.stream()
            .filter(RunInterceptorExtension.class::isInstance).map(RunInterceptorExtension.class::cast)
            .sorted(ORDERING).toList();

        List<StationWrapperExtension> stationWrappers = activeExtensions.stream()
            .filter(StationWrapperExtension.class::isInstance).map(StationWrapperExtension.class::cast)
            .sorted(ORDERING).toList();

        List<ExecutorWrapperExtension> executorWrappers = activeExtensions.stream()
            .filter(ExecutorWrapperExtension.class::isInstance).map(ExecutorWrapperExtension.class::cast)
            .sorted(ORDERING).toList();

        return new RunPlan(runInterceptors, stationWrappers, executorWrappers);
    }
}
