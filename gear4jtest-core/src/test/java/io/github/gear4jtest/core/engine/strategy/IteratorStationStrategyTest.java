package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IteratorStationStrategyTest {
    @Test
    void should_propagate_and_restore_current_item_id_for_child_chain() {
        // Given
        IteratorStation<List<String>, String> station = new IteratorStation.Builder<List<String>, String>("iterator")
                .iterableFunction(input -> input)
                .sequence(SequenceStation.Builder.<String>create("chain").build())
                .build();
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace())
                .build();
        StationExecutionContext stationContext = new TestStationExecutionContext(globalContext,
                StationLogTrace.start(UUID.randomUUID(), "iterator", null));

        List<String> seenItemIds = new ArrayList<>();
        IteratorStationStrategy strategy = new IteratorStationStrategy();

        // When
        Object result = strategy.doExecute(station, List.of("a", "b"), (input, child, ctx) -> {
            seenItemIds.add(ctx.getGlobalContext().getCurrentItemId());
            StationLogTrace childLog = StationLogTrace.start(UUID.randomUUID(), child.getId(), null);
            childLog.setStatus(StationLogStatus.SUCCEEDED);
            childLog.setOutput(input + "-out");
            return childLog;
        }, stationContext);

        // Then
        assertThat(seenItemIds).containsExactly("iterator#item-0", "iterator#item-1");
        assertThat(globalContext.getCurrentItemId()).isNull();
        assertThat((Collection<String>) result).containsExactly("a-out", "b-out");
    }

    private record TestStationExecutionContext(ExecutionContext globalContext,
                                               StationLogTrace stationLogTrace)
            implements StationExecutionContext {
        @Override
        public String getOperationId() {
            return "iterator";
        }

        @Override
        public StationKind getKind() {
            return StationKind.ITERATOR;
        }

        @Override
        public ExecutionContext getGlobalContext() {
            return globalContext;
        }

        @Override
        public StationLogTrace getRecord() {
            return stationLogTrace;
        }

        @Override
        public <T> Optional<T> getCapability(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public ResolvedParameters getResolvedParameters() {
            return new ResolvedParameters();
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
