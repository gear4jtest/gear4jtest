package io.github.gear4jtest.core.engine.support;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.assemblyline.AssemblyLineReference;
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
        List<AssemblyLineReference> assemblyLineCallStackSnapshot = ctx.getGlobalContext().getAssemblyLineCallStack()
                .snapshot();

        return () -> {
            ExecutionContext context = ctx.getGlobalContext();
            try (var ignoredCallStack = context.getAssemblyLineCallStack()
                    .restoreSnapshot(assemblyLineCallStackSnapshot);
                    var ignoredItem = context.enterItem(itemId);
                    var ignoredBranch = context.enterBranch(branchId);
                    var ignoredParent = context.enterParentOperation(parentOperationId)) {
                Object safeInput = inputSupplier.get();
                return runner.run(safeInput, station, ctx);
            }
        };
    }
}
