package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
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
     * {@code AssemblyLineCallStation} running in {@code NESTED_RUN} mode requires
     * the overload accepting a {@link NestedAssemblyLineExecutor}.
     * </p>
     */
    public static StrategyRegistry defaultRegistry() {
        return defaultRegistry(NestedAssemblyLineExecutor.unsupported());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor) {
        return defaultRegistry(nestedAssemblyLineExecutor, WorkerConcurrencyManager.global(),
                               WorkerConcurrencyConfiguration.defaults(), ParallelExecutionConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager) {
        return defaultRegistry(nestedAssemblyLineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE),
                               ParallelExecutionConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyPolicy workerConcurrencyPolicy) {
        return defaultRegistry(nestedAssemblyLineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(workerConcurrencyPolicy),
                               ParallelExecutionConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyPolicy workerConcurrencyPolicy,
                                                   WorkerLockAcquisitionPolicy lockAcquisitionPolicy) {
        return defaultRegistry(nestedAssemblyLineExecutor, workerConcurrencyManager,
                               WorkerConcurrencyConfiguration.defaults()
                                       .withConcurrencyPolicy(workerConcurrencyPolicy)
                                       .withLockAcquisitionPolicy(lockAcquisitionPolicy),
                               ParallelExecutionConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyConfiguration workerConcurrencyConfiguration) {
        return defaultRegistry(nestedAssemblyLineExecutor, workerConcurrencyManager, workerConcurrencyConfiguration,
                               ParallelExecutionConfiguration.defaults());
    }

    public static StrategyRegistry defaultRegistry(NestedAssemblyLineExecutor nestedAssemblyLineExecutor,
                                                   WorkerConcurrencyManager workerConcurrencyManager,
                                                   WorkerConcurrencyConfiguration workerConcurrencyConfiguration,
                                                   ParallelExecutionConfiguration parallelExecutionConfiguration) {
        Objects.requireNonNull(nestedAssemblyLineExecutor, "nestedAssemblyLineExecutor must not be null");
        Objects.requireNonNull(workerConcurrencyManager, "workerConcurrencyManager must not be null");
        Objects.requireNonNull(workerConcurrencyConfiguration, "workerConcurrencyConfiguration must not be null");
        Objects.requireNonNull(parallelExecutionConfiguration, "parallelExecutionConfiguration must not be null");
        return new StrategyRegistry(
                List.of(WorkStationStrategy.builder()
                        .concurrencyManager(workerConcurrencyManager)
                        .concurrencyConfiguration(workerConcurrencyConfiguration)
                        .build(),
                        new SequenceStationStrategy(), new IteratorStationStrategy(),
                        new IfElseContainerStationStrategy(),
                        new ContainerStationStrategy(parallelExecutionConfiguration), new SignalStationStrategy(),
                        new AssemblyLineCallStationStrategy(nestedAssemblyLineExecutor)));
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
