package io.github.gear4jtest.core.engine;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;

final class AssemblyLineRunCleanup {
    private AssemblyLineRunCleanup() {
    }

    static Runnable cleanup(ExecutionContext context, ExecutionContextRegistry executionContextRegistry) {
        return () -> {
            try {
                context.getSideComputeContext().cancelUnresolvedFutures();
                context.getServices().getStationScopedResources().clearAll();
            } finally {
                executionContextRegistry.remove(context.getExecutionId(), context);
            }
        };
    }
}
