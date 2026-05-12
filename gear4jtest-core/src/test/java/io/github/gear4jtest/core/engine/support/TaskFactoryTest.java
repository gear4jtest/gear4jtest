package io.github.gear4jtest.core.engine.support;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskFactoryTest {

    @Test
    void should_propagate_item_id_and_parent_operation_id_inside_async_task() throws Exception {
        // Given
        TaskFactory taskFactory = new TaskFactory();
        ExecutionSupport support = new ExecutionSupport(ExecutorDecorator.noOp(), taskFactory, null);

        AssemblyRunTrace assemblyRun = new AssemblyRunTrace(UUID.randomUUID(), "pipeline-1", Map.of());
        var resourceFactory = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
        ExecutionContext globalContext = new ExecutionContext(UUID.randomUUID(), "pipeline-1",
                new ExecutionServices(
                        new EventManager(EventHandlingDefinition.builder().build(), new ExecutionContextRegistry()),
                        resourceFactory),
                assemblyRun);

        UUID parentOperationId = UUID.randomUUID();
        globalContext.setCurrentItemId("outer-item");
        globalContext.pushParentOperationId(parentOperationId);

        StationLogTrace parentRecord = StationLogTrace.start(globalContext.getExecutionId(), "parent", null);
        parentRecord.setContext(new HashMap<>());
        parentRecord.setStatus(StationLogStatus.RUNNING);

        StationExecutionContext operationExecution = new DefaultStationExecutionContext("parent", StationKind.CONTAINER,
                globalContext, parentRecord, support);

        DummyStation child = new DummyStation("child");

        AtomicReference<String> seenItemId = new AtomicReference<>();
        AtomicReference<UUID> seenParentOperationId = new AtomicReference<>();

        StationRunner runner = (input, station, ctx) -> {
            seenItemId.set(ctx.getGlobalContext().getCurrentItemId());
            seenParentOperationId.set(ctx.getGlobalContext().getCurrentParentOperationId());

            StationLogTrace childLog = StationLogTrace.start(ctx.getGlobalContext().getExecutionId(), station.getId(),
                                                             null);
            childLog.setContext(new HashMap<>());
            childLog.markSuccess("ok");
            return childLog;
        };

        Callable<StationLogTrace> task = taskFactory.createTask(() -> "payload", child, runner, operationExecution,
                                                                "child-item");

        // When
        StationLogTrace result = task.call();

        // Then
        assertThat(result.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(seenItemId.get()).isEqualTo("child-item");
        assertThat(seenParentOperationId.get()).isEqualTo(parentOperationId);

        assertThat(globalContext.getCurrentItemId()).isEqualTo("outer-item");
        assertThat(globalContext.getCurrentParentOperationId()).isEqualTo(parentOperationId);
    }

    private static final class DummyStation extends AbstractStation<Object, Object> {
        private DummyStation(String id) {
            super(id, StationKind.OTHER);
        }
    }
}
