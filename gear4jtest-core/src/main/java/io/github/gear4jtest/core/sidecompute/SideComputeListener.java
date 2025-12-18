package io.github.gear4jtest.core.sidecompute;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.model.ExecutionContext;

/**
 * Listener branché sur un EventBus dédié (ex: "sideCompute").
 * Il exécute des SideComputer en arrière-plan (thread du bus) et range les
 * résultats dans le SideComputeContext du ExecutionContext associé à l'exécution.
 */
public final class SideComputeListener implements EventListener<Event> {

    private final List<SideComputer<?>> computers;
    private final ExecutionContextRegistry registry;

    public SideComputeListener(List<SideComputer<?>> computers,
                               ExecutionContextRegistry registry) {
        this.computers = computers;
        this.registry = registry;
    }

    @Override
    public void handleEvent(Event event) {
        if (!(event instanceof OperationCompletedEvent completed)) {
            return;
        }

        ExecutionContext ctx = registry.get(completed.getExecutionId());
        if (ctx == null) {
            return;
        }

        SideComputeContext scCtx = ctx.getSideComputeContext();

        for (SideComputer<?> sc : computers) {
            if (sc.matches(completed)) {
                runCompute(completed, scCtx, sc);
            }
        }
    }

    private <R> void runCompute(OperationCompletedEvent ev,
                                SideComputeContext scCtx,
                                SideComputer<R> sc) {

        CompletableFuture<R> future = scCtx.getOrCreateFuture(sc.key());

        try {
            R result = sc.computer().apply(ev);
            future.complete(result);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }
}
