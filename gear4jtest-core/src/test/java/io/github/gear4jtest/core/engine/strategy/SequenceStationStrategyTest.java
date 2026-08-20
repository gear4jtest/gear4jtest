package io.github.gear4jtest.core.engine.strategy;

import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.trace.StationTrace;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceStationStrategyTest {
    @Test
    void should_expose_every_executed_step_as_a_sub_operation_in_execution_order() {
        DummyStation first = new DummyStation("first");
        DummyStation second = new DummyStation("second");
        SequenceStation<String, String> sequence = SequenceStation.Builder.<String>create("sequence")
                .next(first)
                .next(second)
                .build();
        StationExecutionContext context = newStationContext();

        Object output = new SequenceStationStrategy().doExecute(sequence, "input", (input, child, childContext) -> {
            StationLogTrace childLog = StationLogTrace.start(UUID.randomUUID(), child.getId(), null);
            childLog.setStatus(StationLogStatus.SUCCEEDED);
            childLog.setOutput(input + "-" + child.getId());
            return childLog;
        }, context);

        assertThat(output).isEqualTo("input-first-second");
        assertThat(context.getRecord().getSubOperations())
                .extracting(StationTrace::getOperationId)
                .containsExactly("first", "second");
    }

    private static StationExecutionContext newStationContext() {
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace())
                .build();
        return new DefaultStationExecutionContext("sequence", StationKind.SEQUENCE, globalContext,
                StationLogTrace.start(UUID.randomUUID(), "sequence", null), null);
    }

    private static final class DummyStation extends AbstractStation<String, String> {
        private DummyStation(String id) {
            super(id, StationKind.CUSTOM, null, null, null, true, null, null);
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
