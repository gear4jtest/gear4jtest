package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        StationExecutionContext stationContext = new DefaultStationExecutionContext("iterator", StationKind.ITERATOR,
                globalContext, StationLogTrace.start(UUID.randomUUID(), "iterator", null), null);

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
        assertThat(result).isEqualTo(List.of("a-out", "b-out"));
    }

    @Test
    void should_reject_an_iterable_that_exceeds_the_configured_item_limit() {
        // Given
        IteratorStation<List<String>, String> station = new IteratorStation.Builder<List<String>, String>("iterator")
                .iterableFunction(input -> input)
                .sequence(SequenceStation.Builder.<String>create("chain").build())
                .maxItems(2L)
                .build();
        StationExecutionContext stationContext = newStationContext();
        IteratorStationStrategy strategy = new IteratorStationStrategy();

        // When / Then
        assertThatThrownBy(() -> strategy.doExecute(station, List.of("a", "b", "c"),
                                                    IteratorStationStrategyTest::successfulChild, stationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Iterator station 'iterator' exceeded its maximum item count of 2");
    }

    @Test
    void should_stop_between_items_when_run_cancellation_is_requested() {
        // Given
        IteratorStation<List<String>, String> station = new IteratorStation.Builder<List<String>, String>("iterator")
                .iterableFunction(input -> input)
                .sequence(SequenceStation.Builder.<String>create("chain").build())
                .build();
        StationExecutionContext stationContext = newStationContext();
        AtomicInteger executions = new AtomicInteger();
        IteratorStationStrategy strategy = new IteratorStationStrategy();

        // When
        Object result = strategy.doExecute(station, List.of("a", "b"), (input, child, context) -> {
            executions.incrementAndGet();
            context.getGlobalContext().getCancellationToken().cancel("test cancellation");
            return successfulChild(input, child, context);
        }, stationContext);

        // Then
        assertThat(executions).hasValue(1);
        assertThat(result).isEqualTo(List.of("a"));
        assertThat(stationContext.getRecord().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
    }

    @Test
    void cancellation_shouldNotBeOverwrittenByAnEarlierCollectedFailure() {
        // Given
        IteratorStation<List<String>, String> station = new IteratorStation.Builder<List<String>, String>("iterator")
                .iterableFunction(input -> input)
                .sequence(SequenceStation.Builder.<String>create("chain").build())
                .flowConfig(new FlowConfig(FailurePolicy.COLLECT_AND_FAIL, StopPolicy.PROPAGATE_STOP,
                        CancelPolicy.PROPAGATE_CANCEL))
                .build();
        StationExecutionContext stationContext = newStationContext();

        // When
        Object result = new IteratorStationStrategy().doExecute(station, List.of("a", "b"),
                                                                (input, child, context) -> {
                                                                    context.getGlobalContext().getCancellationToken()
                                                                            .cancel("test cancellation");
                                                                    StationLogTrace childLog = StationLogTrace
                                                                            .start(UUID.randomUUID(), child.getId(),
                                                                                   null);
                                                                    childLog.markFailed(new IllegalStateException(
                                                                            "collected failure"));
                                                                    return childLog;
                                                                }, stationContext);

        // Then
        assertThat(result).isEqualTo(List.of());
        assertThat(stationContext.getRecord().getStatus()).isEqualTo(StationLogStatus.CANCELLED);
    }

    @Test
    void should_reject_a_null_iterable_source_with_station_context() {
        // Given
        IteratorStation<String, String> station = new IteratorStation.Builder<String, String>("iterator")
                .<String>iterableFunction(input -> null)
                .sequence(SequenceStation.Builder.<String>create("chain").build())
                .build();

        // When / Then
        assertThatThrownBy(() -> new IteratorStationStrategy().doExecute(station, "input",
                                                                         IteratorStationStrategyTest::successfulChild,
                                                                         newStationContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Iterator station 'iterator' requires a non-null Iterable source");
    }

    private static StationLogTrace successfulChild(Object input,
                                                   AbstractStation<?, ?> child,
                                                   StationExecutionContext context) {
        StationLogTrace childLog = StationLogTrace.start(UUID.randomUUID(), child.getId(), null);
        childLog.setStatus(StationLogStatus.SUCCEEDED);
        childLog.setOutput(input);
        return childLog;
    }

    private static StationExecutionContext newStationContext() {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace())
                .build();
        return new DefaultStationExecutionContext("iterator", StationKind.ITERATOR, globalContext,
                StationLogTrace.start(UUID.randomUUID(), "iterator", null), null);
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
