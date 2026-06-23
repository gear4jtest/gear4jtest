package io.github.gear4jtest.core.engine.strategy;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryDeepCoverageTest {
    @Test
    void defaultRegistryOverloads_shouldCreateUsableWorkStationStrategies() {
        // Given
        WorkStation<String, String> station = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("work")
                .build();

        // When / Then
        assertThat(StrategyRegistry.defaultRegistry().getStrategy(station)).isInstanceOf(WorkStationStrategy.class);
        assertThat(StrategyRegistry.defaultRegistry(
                                                    NestedAssemblyLineExecutor.unsupported(),
                                                    new WorkerConcurrencyManager())
                .getStrategy(station))
                .isInstanceOf(WorkStationStrategy.class);
        assertThat(StrategyRegistry.defaultRegistry(
                                                    NestedAssemblyLineExecutor.unsupported(),
                                                    new WorkerConcurrencyManager(),
                                                    WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS)
                .getStrategy(station))
                .isInstanceOf(WorkStationStrategy.class);
        assertThat(StrategyRegistry.defaultRegistry(
                                                    NestedAssemblyLineExecutor.unsupported(),
                                                    new WorkerConcurrencyManager(),
                                                    WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,
                                                    WorkerLockAcquisitionPolicy.FAIL_FAST)
                .getStrategy(station))
                .isInstanceOf(WorkStationStrategy.class);
        assertThat(StrategyRegistry.defaultRegistry(
                                                    NestedAssemblyLineExecutor.unsupported(),
                                                    new WorkerConcurrencyManager(),
                                                    WorkerConcurrencyConfiguration.defaults(),
                                                    ParallelExecutionConfiguration.defaults())
                .getStrategy(station))
                .isInstanceOf(WorkStationStrategy.class);
    }

    @Test
    void defaultRegistry_shouldValidateRequiredCollaborators() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();

        assertThatThrownBy(() -> StrategyRegistry.defaultRegistry(null, manager,
                                                                  WorkerConcurrencyConfiguration.defaults(),
                                                                  ParallelExecutionConfiguration.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("nestedAssemblyLineExecutor must not be null");
        assertThatThrownBy(() -> StrategyRegistry.defaultRegistry(NestedAssemblyLineExecutor.unsupported(), null,
                                                                  WorkerConcurrencyConfiguration.defaults(),
                                                                  ParallelExecutionConfiguration.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workerConcurrencyManager must not be null");
        assertThatThrownBy(() -> StrategyRegistry.defaultRegistry(NestedAssemblyLineExecutor.unsupported(), manager,
                                                                  null, ParallelExecutionConfiguration.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workerConcurrencyConfiguration must not be null");
        assertThatThrownBy(() -> StrategyRegistry.defaultRegistry(NestedAssemblyLineExecutor.unsupported(), manager,
                                                                  WorkerConcurrencyConfiguration.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parallelExecutionConfiguration must not be null");
    }

    @Test
    void getStrategy_shouldCacheByStationClassAndFailForUnsupportedStations() {
        // Given
        WorkStation<String, String> first = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("first")
                .build();
        WorkStation<String, String> second = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("second")
                .build();
        StrategyRegistry registry = StrategyRegistry.defaultRegistry();
        StrategyRegistry empty = new StrategyRegistry(List.of());

        // When
        StationExecutionStrategy<WorkStation<String, String>> firstStrategy = registry.getStrategy(first);
        StationExecutionStrategy<WorkStation<String, String>> secondStrategy = registry.getStrategy(second);

        // Then
        assertThat(secondStrategy).isSameAs(firstStrategy);
        assertThatThrownBy(() -> empty.getStrategy(new UnsupportedStation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No strategy for class");
    }

    private static final class UnsupportedStation extends AbstractStation<String, String> {
        private UnsupportedStation() {
            super("unsupported", StationKind.CUSTOM, null, null, null, false, null, null);
        }
    }

    private static final class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }
}
