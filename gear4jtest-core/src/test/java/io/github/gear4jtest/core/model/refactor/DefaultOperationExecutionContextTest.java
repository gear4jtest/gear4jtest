package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

class DefaultOperationExecutionContextTest {

    @Test
    void constructor_shouldExposeOperationAndGlobalContext() {
        ExecutionContext global =
                new ExecutionContext("pipeline-1", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec-1", "op-1", null);

        DefaultOperationExecutionContext ctx =
                new DefaultOperationExecutionContext("op-1", OperationKind.PROCESSING, global, record);

        assertThat(ctx.getOperationId()).isEqualTo("op-1");
        assertThat(ctx.getKind()).isEqualTo(OperationKind.PROCESSING);
        assertThat(ctx.getGlobalContext()).isSameAs(global);
        assertThat(ctx.getRecord()).isSameAs(record);
    }

    @Test
    void capabilities_shouldBeEmptyByDefaultAndReturnValueWhenAdded() {
        ExecutionContext global =
                new ExecutionContext("pipeline-1", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec-1", "op-1", null);

        DefaultOperationExecutionContext ctx =
                new DefaultOperationExecutionContext("op-1", OperationKind.PROCESSING, global, record);

        assertThat(ctx.getCapability(String.class)).isEmpty();

        ctx.addCapability(String.class, "value");
        ctx.addCapability(Integer.class, 42);

        assertThat(ctx.getCapability(String.class)).contains("value");
        assertThat(ctx.getCapability(Integer.class)).contains(42);
        assertThat(ctx.getCapability(Long.class)).isEmpty();
    }
}
