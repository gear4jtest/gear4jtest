package io.github.gear4jtest.core.engine.support;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class TaskFactory {

    public Callable<StationLog> createTask(
            Supplier<?> inputSupplier,
            AbstractStation station,
            StationRunner runner,
            StationExecutionContext ctx,
            String itemId) {

        UUID parentOperationId = ctx.getGlobalContext().getCurrentParentOperationId();

        return () -> withItemId(itemId, ctx.getGlobalContext(), () ->
                withParentOperationId(parentOperationId, ctx.getGlobalContext(), () -> {
                    Object safeInput = inputSupplier.get();
                    return runner.run(safeInput, station, ctx);
                }));
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

    private <T> T withParentOperationId(UUID parentOperationId, ExecutionContext context, Supplier<T> action) {
        if (parentOperationId == null) {
            return action.get();
        }

        context.pushParentOperationId(parentOperationId);
        try {
            return action.get();
        } finally {
            context.popParentOperationId();
        }
    }
}
