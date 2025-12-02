package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;

@ExtendWith(MockitoExtension.class)
class ExecutionContextTest {

    @Mock
    private EventManager eventManager;

    @Mock
    private ResourceFactory resourceFactory;

    @Mock
    private PipelineExecutionManager pipelineExecutionManager;

    @Test
    void constructor_shouldInitializeExecutionIdAndPipelineIdAndDependencies() {
        String pipelineId = "pipe-123";

        ExecutionContext context =
                new ExecutionContext(UUID.randomUUID(), pipelineId, eventManager, resourceFactory, pipelineExecutionManager, null);

        assertThat(context.getPipelineId()).isEqualTo(pipelineId);
        assertThat(context.getExecutionId()).isNotNull()
                .isInstanceOf(UUID.class);

        assertThat(context.getEventManager()).isSameAs(eventManager);
        assertThat(context.getResourceFactory()).isSameAs(resourceFactory);
        assertThat(context.getExecutionManager()).isSameAs(pipelineExecutionManager);
        assertThat(context.getContext()).isNotNull().isEmpty();
    }

    @Test
    void putAndGet_shouldStoreAndRetrieveTypedValues() {
        ExecutionContext context =
                new ExecutionContext(UUID.randomUUID(), "pipe", eventManager, resourceFactory, pipelineExecutionManager, null);

        context.put("int", 42);
        context.put("str", "hello");

        Integer intVal = context.get("int", Integer.class);
        String strVal = context.get("str", String.class);

        assertThat(intVal).isEqualTo(42);
        assertThat(strVal).isEqualTo("hello");
    }
}
