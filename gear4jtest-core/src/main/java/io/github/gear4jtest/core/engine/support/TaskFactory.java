package io.github.gear4jtest.core.engine.support;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class TaskFactory {
    public Callable<StationLogTrace> createTask(Supplier<?> inputSupplier,
                                                AbstractStation<?, ?> station,
                                                StationRunner runner,
                                                StationExecutionContext ctx,
                                                String itemId) {
        return createTask(inputSupplier, station, runner, ctx, itemId, ctx.getGlobalContext().getCurrentBranchId());
    }

    public Callable<StationLogTrace> createTask(Supplier<?> inputSupplier,
                                                AbstractStation<?, ?> station,
                                                StationRunner runner,
                                                StationExecutionContext ctx,
                                                String itemId,
                                                String branchId) {

        UUID parentOperationId = ctx.getGlobalContext().getCurrentParentOperationId();

        return () -> {
            ExecutionContext context = ctx.getGlobalContext();
            try (var ignoredItem = context.enterItem(itemId);
                    var ignoredBranch = context.enterBranch(branchId);
                    var ignoredParent = context.enterParentOperation(parentOperationId)) {
                Object safeInput = inputSupplier.get();
                return runner.run(safeInput, station, ctx);
            }
        };
    }
}
