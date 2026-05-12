package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyStrategy;
import io.github.gear4jtest.core.engine.support.WorkerIntrospector;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class WorkStationStrategy extends AbstractStationStrategy<WorkStation<?, ?>> {
    /**
     * Shared concurrency manager used to protect stateful operators during station
     * execution.
     */
    private static final WorkerConcurrencyManager CONCURRENCY_MANAGER = new WorkerConcurrencyManager();
    /**
     * Thread-local guard acquired for the current execution, if any.
     */
    private static final ThreadLocal<WorkerConcurrencyGuard> CURRENT_GUARD = new ThreadLocal<>();

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return WorkStation.class.isAssignableFrom(type);
    }

    @Override
    public void setUp(WorkStation<?, ?> station, Object input, StationExecutionContext operationExecution) {
        var services = operationExecution.getServices();

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Operator<?, ?>> operatorType = (Class) station.getType();

        Operator<?, ?> operation;
        if (station.isReuseOperatorInstanceWithinRun()) {
            operation = services
                    .getOrCreateStationResource(station.getId(), operatorType,
                                                () -> services.getResourceFactory().getResource(operatorType));
        } else {
            operation = services.getResourceFactory().getResource(operatorType);
        }

        ((DefaultStationExecutionContext) operationExecution).addCapability(Operator.class, operation);
        var parameters = WorkerParamsInjector.Parameters.newBuilder();
        Optional.ofNullable(station.getParameters()).stream().flatMap(List::stream)
                .forEach(a -> parameters.withParameter((WorkerParamsInjector.ParameterModel) a));
        ((DefaultStationExecutionContext) operationExecution).addCapability(WorkerParamsInjector.Parameters.class,
                                                                            parameters.build());

        if (!isStateful(operationExecution)) {
            return;
        }

        WorkerConcurrencyGuard guard = CONCURRENCY_MANAGER.guardFor(operation, concurrencyStrategy());

        // If FAIL_FAST cannot acquire the guard, beforeUse() throws before the
        // ThreadLocal is set.
        guard.beforeUse();
        CURRENT_GUARD.set(guard);
    }

    @Override
    public Object doExecute(WorkStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        return StationContextUtils.applyTransformer(input, operationExecution)
                .orElseThrow(() -> new IllegalStateException(
                        "No transformer present found in operation execution context"));
    }

    @Override
    protected void release(WorkStation<?, ?> station,
                           Object result,
                           StationExecutionContext context,
                           List<Throwable> errors) {
        try {
            if (isStateful(context)) {
                WorkerConcurrencyGuard guard = CURRENT_GUARD.get();
                if (guard != null) {
                    guard.afterUse();
                }
            }
        } finally {
            // Clear the ThreadLocal to avoid leaks on pooled threads.
            CURRENT_GUARD.remove();
            // Delegate remaining cleanup to the base strategy.
            super.release(station, result, context, errors);
        }
    }

    /**
     * Returns whether the bound operator is considered stateful.
     *
     * <p>
     * By default the strategy derives this from the operator itself.
     * </p>
     */
    protected boolean isStateful(StationExecutionContext operationExecution) {
        var transformer = StationContextUtils.getTransformer(operationExecution);
        return transformer.isPresent() && WorkerIntrospector.isStateful(transformer.get());
    }

    /**
     * Returns the concurrency strategy used when this station can run concurrently.
     */
    protected WorkerConcurrencyStrategy concurrencyStrategy() {
        return WorkerConcurrencyStrategy.BLOCK_CALLER;
    }
}
