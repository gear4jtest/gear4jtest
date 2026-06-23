package io.github.gear4jtest.core.engine.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.assemblyline.AssemblyLineReference;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.slf4j.MDC;

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
        Map<String, String> mdcContextSnapshot = MDC.getCopyOfContextMap();

        return () -> {
            ExecutionContext context = ctx.getGlobalContext();
            Map<String, String> previousMdcContext = installMdc(mdcContextSnapshot);
            try (var ignoredCallStack = context.getAssemblyLineCallStack()
                    .restoreSnapshot(assemblyLineCallStackSnapshot);
                    var ignoredItem = context.enterItem(itemId);
                    var ignoredBranch = context.enterBranch(branchId);
                    var ignoredParent = context.enterParentOperation(parentOperationId)) {
                Object safeInput = inputSupplier.get();
                return runner.run(safeInput, station, ctx);
            } finally {
                restoreMdc(previousMdcContext);
            }
        };
    }

    private static Map<String, String> installMdc(Map<String, String> context) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        return previousContext;
    }

    private static void restoreMdc(Map<String, String> previousContext) {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
