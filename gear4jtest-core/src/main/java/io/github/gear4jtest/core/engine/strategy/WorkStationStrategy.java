package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.api.config.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameters;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerIntrospector;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.exception.ConcurrentTransformerUseException;
import io.github.gear4jtest.core.exception.ResourceResolutionException;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class WorkStationStrategy extends AbstractStationStrategy<WorkStation<?, ?>> {
    private final WorkerConcurrencyManager concurrencyManager;
    private final WorkerConcurrencyConfiguration concurrencyConfiguration;
    private final WorkerParamsInjector paramsInjector = new WorkerParamsInjector();

    public static Builder builder() {
        return new Builder();
    }

    public WorkStationStrategy() {
        this(builder());
    }

    private WorkStationStrategy(Builder builder) {
        this.concurrencyManager = Objects.requireNonNull(builder.concurrencyManager,
                                                         "concurrencyManager must not be null");
        this.concurrencyConfiguration = Objects.requireNonNull(builder.concurrencyConfiguration,
                                                               "concurrencyConfiguration must not be null");
    }

    public static final class Builder {
        private WorkerConcurrencyManager concurrencyManager = WorkerConcurrencyManager.global();
        private WorkerConcurrencyConfiguration concurrencyConfiguration = WorkerConcurrencyConfiguration.defaults();

        private Builder() {
        }

        public Builder concurrencyManager(WorkerConcurrencyManager concurrencyManager) {
            this.concurrencyManager = concurrencyManager;
            return this;
        }

        public Builder concurrencyConfiguration(WorkerConcurrencyConfiguration concurrencyConfiguration) {
            this.concurrencyConfiguration = concurrencyConfiguration;
            return this;
        }

        public Builder concurrencyPolicy(WorkerConcurrencyPolicy concurrencyPolicy) {
            this.concurrencyConfiguration = this.concurrencyConfiguration.withConcurrencyPolicy(concurrencyPolicy);
            return this;
        }

        public Builder lockAcquisitionPolicy(WorkerLockAcquisitionPolicy lockAcquisitionPolicy) {
            this.concurrencyConfiguration = this.concurrencyConfiguration
                    .withLockAcquisitionPolicy(lockAcquisitionPolicy);
            return this;
        }

        public WorkStationStrategy build() {
            return new WorkStationStrategy(this);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return WorkStation.class.isAssignableFrom(type);
    }

    @Override
    public void setUp(WorkStation<?, ?> station, Object input, StationExecutionContext operationExecution) {
        var services = operationExecution.getServices();

        Class<? extends Operator<?, ?>> operatorType = station.getType();

        Operator<?, ?> operation = resolveOperator(station, operatorType, services);

        EngineStationContexts.addCapability(operationExecution, Operator.class, operation);
        var parameters = StationParameters.newBuilder();
        Optional.ofNullable(station.getParameters()).stream()
                .flatMap(List::stream)
                .forEach(parameters::withParameter);
        EngineStationContexts.addCapability(operationExecution, StationParameters.class, parameters.build());

        if (!shouldProtectWorker(station, operationExecution)) {
            return;
        }

        WorkerConcurrencyGuard guard = concurrencyManager.guardFor(operation);
        try {
            guard.beforeUse(lockAcquisitionPolicy(), concurrencyConfiguration.lockWaitTimeout());
        } catch (ConcurrentTransformerUseException e) {
            throw new ConcurrentTransformerUseException("Worker instance cannot be invoked concurrently: "
                    + operation.getClass().getName() + ". Concurrent invocation is protected by "
                    + concurrencyPolicy() + " with " + lockAcquisitionPolicy() + " and timeout "
                    + concurrencyConfiguration.lockWaitTimeout() + ". " + lockFailureAdvice(), e);
        }
        EngineStationContexts.addCapability(operationExecution, WorkerConcurrencyGuard.class, guard);
    }

    @Override
    protected void afterBeforeProcessors(WorkStation<?, ?> station,
                                         Object input,
                                         StationExecutionContext operationExecution) {
        if (station.getParameters() != null && !station.getParameters().isEmpty()) {
            paramsInjector.beforeExecution(input, operationExecution);
        }
    }

    @Override
    protected void afterProcessors(WorkStation<?, ?> station,
                                   Object result,
                                   StationExecutionContext operationExecution) {
        if (station.getParameters() != null && !station.getParameters().isEmpty()) {
            paramsInjector.afterExecution(result, operationExecution);
        }
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
            context.getCapability(WorkerConcurrencyGuard.class).ifPresent(WorkerConcurrencyGuard::afterUse);
        } finally {
            // Delegate remaining cleanup to the base strategy.
            super.release(station, result, context, errors);
        }
    }

    private boolean shouldProtectWorker(WorkStation<?, ?> station, StationExecutionContext operationExecution) {
        if (concurrencyPolicy() == WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS) {
            return false;
        }
        if (concurrencyPolicy() == WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY
                && !station.isReuseOperatorInstanceWithinRun()) {
            return false;
        }
        return isStateful(operationExecution);
    }

    private <T extends Operator<?, ?>> T resolveOperator(WorkStation<?, ?> station,
                                                         Class<T> operatorType,
                                                         ExecutionServices services) {
        if (station.isReuseOperatorInstanceWithinRun()) {
            return services.getOrCreateStationResource(station.getId(), operatorType,
                                                       () -> requireOperator(station, operatorType,
                                                                             services.getResourceFactory()
                                                                                     .getResource(operatorType)));
        }
        return requireOperator(station, operatorType, services.getResourceFactory().getResource(operatorType));
    }

    private <T extends Operator<?, ?>> T requireOperator(WorkStation<?, ?> station,
                                                         Class<T> operatorType,
                                                         Object resource) {
        if (resource == null) {
            throw new ResourceResolutionException("ResourceFactory returned null for operator "
                    + operatorType.getName() + " required by station '" + station.getId() + "'");
        }
        if (!operatorType.isInstance(resource)) {
            throw new ResourceResolutionException("ResourceFactory returned incompatible resource "
                    + resource.getClass().getName() + " for operator " + operatorType.getName()
                    + " required by station '" + station.getId() + "'");
        }
        return operatorType.cast(resource);
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
     * Returns the concurrency policy used when a stateful worker can run
     * concurrently.
     */

    private String lockFailureAdvice() {
        if (lockAcquisitionPolicy() == WorkerLockAcquisitionPolicy.FAIL_FAST) {
            return "Use " + WorkerLockAcquisitionPolicy.BLOCK_CALLER + " to wait up to the configured timeout, "
                    + "increase the lock wait timeout when blocking is enabled, or use "
                    + WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS
                    + " only when the worker is thread-safe.";
        }
        return "Increase the lock wait timeout, use " + WorkerLockAcquisitionPolicy.FAIL_FAST
                + " to fail immediately, or use " + WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS
                + " only when the worker is thread-safe.";
    }

    protected WorkerConcurrencyPolicy concurrencyPolicy() {
        return concurrencyConfiguration.concurrencyPolicy();
    }

    /**
     * Returns how the strategy behaves when a protected worker is already in use.
     */
    protected WorkerLockAcquisitionPolicy lockAcquisitionPolicy() {
        return concurrencyConfiguration.lockAcquisitionPolicy();
    }

    protected WorkerConcurrencyConfiguration concurrencyConfiguration() {
        return concurrencyConfiguration;
    }
}
