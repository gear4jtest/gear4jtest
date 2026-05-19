package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;

public class StrategyRegistry {
    private final Map<Class<? extends AbstractStation<?, ?>>, StationExecutionStrategy<?>> cache = new ConcurrentHashMap<>();
    private final List<StationExecutionStrategy<?>> strategies;

    public StrategyRegistry(List<StationExecutionStrategy<?>> strategies) {
        this.strategies = strategies;
    }

    /**
     * Creates the default registry with nested pipeline execution disabled.
     *
     * <p>
     * This overload is kept for existing tests/custom engines. A
     * {@code PipelineCallStation} running in {@code NESTED_RUN} mode requires the
     * overload accepting a {@link NestedPipelineExecutor}.
     * </p>
     */
    public static StrategyRegistry defaultRegistry() {
        return defaultRegistry(NestedPipelineExecutor.unsupported());
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor) {
        return defaultRegistry(nestedPipelineExecutor, WorkerConcurrencyManager.global(),
                               WorkerConcurrencyConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager) {
        return defaultRegistry(nestedPipelineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE));
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyPolicy workerConcurrencyPolicy) {
        return defaultRegistry(nestedPipelineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(workerConcurrencyPolicy));
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyPolicy workerConcurrencyPolicy,
                                                   WorkerLockAcquisitionPolicy lockAcquisitionPolicy) {
        return defaultRegistry(nestedPipelineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(workerConcurrencyPolicy)
                                       .withLockAcquisitionPolicy(lockAcquisitionPolicy));
    }

    public static StrategyRegistry defaultRegistry(NestedPipelineExecutor nestedPipelineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyConfiguration workerConcurrencyConfiguration) {
        Objects.requireNonNull(nestedPipelineExecutor, "nestedPipelineExecutor must not be null");
        Objects.requireNonNull(workerConcurrencyManager, "workerConcurrencyManager must not be null");
        Objects.requireNonNull(workerConcurrencyConfiguration, "workerConcurrencyConfiguration must not be null");
        return new StrategyRegistry(
                List.of(new WorkStationStrategy(workerConcurrencyManager, workerConcurrencyConfiguration),
                        new SequenceStationStrategy(), new IteratorStationStrategy(),
                        new IfElseContainerStationStrategy(),
                        new ContainerStationStrategy(), new SignalStationStrategy(),
                        new PipelineCallStationStrategy(nestedPipelineExecutor)));
    }

    @SuppressWarnings("unchecked")
    public <S extends AbstractStation<?, ?>> StationExecutionStrategy<S> getStrategy(S station) {
        return (StationExecutionStrategy<S>) cache
                .computeIfAbsent((Class<? extends AbstractStation<?, ?>>) station.getClass(),
                                 type -> strategies.stream()
                                         .filter(s -> s.supports(type))
                                         .findFirst()
                                         .orElseThrow(() -> new IllegalStateException("No strategy for " + type)));
    }
}
