package io.github.gear4jtest.core.engine.support;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class TaskFactory {

    public Callable<StationLog> createTask(Supplier<?> inputSupplier,
                                           AbstractStation station,
                                           StationRunner runner,
                                           StationExecutionContext ctx,
                                           String itemId) {
        return () -> {
            // 1. Scoping (Si withItemId est sur ton GlobalContext)
            return withItemId(itemId, ctx.getGlobalContext(), () -> {
                // 2. Clonage métier (à adapter selon ton code exact)
                Object safeInput = inputSupplier.get();

                // 3. Exécution via le runner
                return runner.run(safeInput, station, ctx);
            });
        };
    }

    private <T> T withItemId(String itemId, ExecutionContext context, Supplier<T> action) {
        String previous = context.getCurrentItemId();
        context.setCurrentItemId(itemId);
        try {
            return action.get();
        } finally {
            context.setCurrentItemId(previous);
        }
    }
}