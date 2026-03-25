package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SideComputeListener implements EventListener<Event> {

    private final List<SideComputer<?, ?>> computers;
    private final ExecutionContextRegistry registry;

    public SideComputeListener(
            List<SideComputer<?, ?>> computers,
            ExecutionContextRegistry registry) {
        this.computers = computers;
        this.registry = registry;
    }

    @Override
    public void handleEvent(Event event) {
        if (!(event instanceof OperationCompletedEvent completed)) {
            return;
        }

        ExecutionContext executionContext = registry.get(completed.getExecutionId());
        if (executionContext == null) {
            return;
        }

        SideComputeContext sideComputeContext = executionContext.getSideComputeContext();

        for (SideComputer<?, ?> sideComputer : computers) {
            if (sideComputer.matches(completed)) {
                runCompute(completed, executionContext, sideComputeContext, sideComputer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T, R> void runCompute(
            OperationCompletedEvent event,
            ExecutionContext executionContext,
            SideComputeContext sideComputeContext,
            SideComputer<?, ?> rawSideComputer) {

        SideComputer<T, R> sideComputer = (SideComputer<T, R>) rawSideComputer;
        CompletableFuture<R> future = sideComputeContext.getOrCreateFuture(sideComputer.key());

        try {
            T computeResult = sideComputer.computer().apply(event);

            for (SideComputeHandler<T> handler : sideComputer.handlers()) {
                handler.handle(sideComputer.key(), event, computeResult, executionContext);
            }

            R finalResult = sideComputer.mapper().apply(computeResult);
            future.complete(finalResult);

        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }
}
