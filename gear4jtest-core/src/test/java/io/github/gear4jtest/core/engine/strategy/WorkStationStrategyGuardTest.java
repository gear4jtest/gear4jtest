package io.github.gear4jtest.core.engine.strategy;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.support.ConcurrencyAwareTransformer;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerStatefulness;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkStationStrategyGuardTest {
    @Test
    void nestedStationSetups_shouldReleaseEachContextGuard() {
        // Given
        OuterOperator outerOperator = new OuterOperator();
        InnerOperator innerOperator = new InnerOperator();
        WorkerConcurrencyManager concurrencyManager = new WorkerConcurrencyManager();
        WorkStationStrategy strategy = WorkStationStrategy.builder()
                .concurrencyManager(concurrencyManager)
                .build();
        ExecutionContext executionContext = ExecutionContext.builder()
                .services(new ExecutionServices(null, new TestResourceFactory(outerOperator, innerOperator)))
                .build();
        DefaultStationExecutionContext outerContext = stationContext("outer", executionContext);
        DefaultStationExecutionContext innerContext = stationContext("inner", executionContext);
        WorkStation<String, String> outerStation = new WorkStation.Builder<String, String, OuterOperator>()
                .id("outer")
                .type(OuterOperator.class)
                .build();
        WorkStation<String, String> innerStation = new WorkStation.Builder<String, String, InnerOperator>()
                .id("inner")
                .type(InnerOperator.class)
                .build();

        // When
        strategy.setUp(outerStation, "input", outerContext);
        strategy.setUp(innerStation, "input", innerContext);
        strategy.release(innerStation, null, innerContext, List.of());
        strategy.release(outerStation, null, outerContext, List.of());

        // Then
        assertThat(outerContext.getCapability(WorkerConcurrencyGuard.class)).isPresent();
        assertThat(innerContext.getCapability(WorkerConcurrencyGuard.class)).isPresent();
        assertGuardCanBeAcquiredAgain(concurrencyManager.guardFor(outerOperator));
        assertGuardCanBeAcquiredAgain(concurrencyManager.guardFor(innerOperator));
    }

    private static DefaultStationExecutionContext stationContext(String id, ExecutionContext executionContext) {
        return new DefaultStationExecutionContext(id, StationKind.PROCESSING, executionContext, null, null);
    }

    private static void assertGuardCanBeAcquiredAgain(WorkerConcurrencyGuard guard) {
        guard.beforeUse(WorkerLockAcquisitionPolicy.FAIL_FAST);
        guard.afterUse();
    }

    private abstract static class StatefulOperator
            implements Operator<String, String>, ConcurrencyAwareTransformer {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }

        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATEFUL;
        }
    }

    private static final class OuterOperator extends StatefulOperator {
    }

    private static final class InnerOperator extends StatefulOperator {
    }

    private record TestResourceFactory(OuterOperator outerOperator, InnerOperator innerOperator)
            implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> type) {
            if (type == OuterOperator.class) {
                return type.cast(outerOperator);
            }
            if (type == InnerOperator.class) {
                return type.cast(innerOperator);
            }
            throw new IllegalArgumentException("Unsupported resource type: " + type.getName());
        }
    }
}
