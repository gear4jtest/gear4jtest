package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyStrategy;
import io.github.gear4jtest.core.engine.support.WorkerIntrospector;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;

public class WorkStationStrategy extends AbstractStationStrategy<WorkStation> {

    /**
     * Manager de concurrence partagé.
     *
     * Si tu veux le scoper à un runtime d'AssemblyLine spécifique,
     * tu pourras injecter un manager plutôt qu'utiliser ce static.
     */
    private static final WorkerConcurrencyManager CONCURRENCY_MANAGER = new WorkerConcurrencyManager();

    /**
     * ThreadLocal pour savoir si on a acquis un lock sur CE thread
     * pour CETTE exécution, et surtout pour ne pas faire d'afterUse()
     * si beforeUse() a échoué.
     */
    private static final ThreadLocal<WorkerConcurrencyGuard> CURRENT_GUARD = new ThreadLocal<>();

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return WorkStation.class.isAssignableFrom(type);
    }

    @Override
    public void setUp(WorkStation station, Object input, StationExecutionContext operationExecution) {
        var execCtx = operationExecution.getGlobalContext();

        Operator<?, ?> operation;
        if (station.isReuseOperatorInstanceWithinRun()) {
            operation = execCtx.getOrCreateStationResource(
                    station.getId(),
                    (Class<Operator>) station.getType(),
                    () -> execCtx.getResourceFactory().getResource((Class<Operator>) station.getType()));
        } else {
            operation = execCtx.getResourceFactory().getResource((Class<Operator>) station.getType());
        }

        ((DefaultStationExecutionContext) operationExecution).addCapability(Operator.class, operation);
        var parameters = WorkerParamsInjector.Parameters.newBuilder();
        Optional.ofNullable(station.getParameters()).stream()
                .flatMap(List::stream)
                .forEach(a -> parameters.withParameter((WorkerParamsInjector.ParameterModel) a));
        ((DefaultStationExecutionContext) operationExecution).addCapability(WorkerParamsInjector.Parameters.class, parameters.build());

        if (!isStateful(operationExecution)) {
            return;
        }

        WorkerConcurrencyGuard guard =
                CONCURRENCY_MANAGER.guardFor(operation, concurrencyStrategy());

        // Si beforeUse() FAIL_FAST et échoue, il va jeter avant qu'on pose le ThreadLocal.
        guard.beforeUse();
        CURRENT_GUARD.set(guard);
    }

    @Override
    public Object doExecute(WorkStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        var transformer = StationContextUtils.getTypedTransformer(operationExecution);
        if (transformer.isEmpty()) {
            throw new IllegalStateException("No transformer present found in operation execution context");
        }
        return transformer.get().transform(input, operationExecution);
    }

    @Override
    protected void release(WorkStation station, Object result, StationExecutionContext context, List<Throwable> errors) {
        try {
            if (isStateful(context)) {
                WorkerConcurrencyGuard guard = CURRENT_GUARD.get();
                if (guard != null) {
                    guard.afterUse();
                }
            }
        } finally {
            // Nettoyage du ThreadLocal pour éviter les fuites sur les pools de threads
            CURRENT_GUARD.remove();
            // Et on laisse la super-classe faire son éventuel cleanup
            super.release(station, result, context, errors);
        }
    }

    /**
     * Indique si cette opération est stateful.
     * Par défaut, on déduit cela automatiquement depuis le transformer.
     */
    protected boolean isStateful(StationExecutionContext operationExecution) {
        var transformer = StationContextUtils.getTypedTransformer(operationExecution);
        return transformer.isPresent() && WorkerIntrospector.isStateful(transformer.get());
    }

    /**
     * Stratégie de concurrence utilisée lorsque cette opération est exécutée
     * de manière concurrente (iteration parallèle, containers parallélisés, etc.).
     */
    protected WorkerConcurrencyStrategy concurrencyStrategy() {
        return WorkerConcurrencyStrategy.BLOCK_CALLER;
    }
}
