package io.github.gear4jtest.core.model.refactor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

class UnaryIfElseContainerDefinitionTest {

    static class ConstantOperation extends AbstractOperationDefinition<String, String> {

        private final String constant;

        ConstantOperation(String id, String constant) {
            super(id, OperationKind.PROCESSING);
            this.constant = constant;
        }

        @Override
        protected void setUp(String input, ExecutionContext context, OperationExecutionContext operationExecution) {
            // no-op
        }

        @Override
        protected String doExecute(String input,
                                   ExecutionContext context,
                                   OperationExecutionContext operationExecution) {
            return constant;
        }
    }

    @Test
    void run_shouldExecuteMatchingBranchWhenConditionTrue() {
        var execManager = new InMemoryExecutionManager();
        var ctx = new ExecutionContext(UUID.randomUUID(), "pipeline-if", null, null, execManager, null);

        ConstantOperation yesOp = new ConstantOperation("yes-op", "YES");
        ConstantOperation noOp = new ConstantOperation("no-op", "NO");

        Condition<String> containsYes = (input, context) -> input.contains("yes");

        UnaryIfElseContainerDefinition.Builder<String> builder =
                new UnaryIfElseContainerDefinition.Builder<>();

        UnaryIfElseContainerDefinition<String> container =
                builder.conditionally(yesOp, containsYes)
                       .elseOp(noOp);

        OperationExecutionRecord rec = container.run("this is yes", ctx);

        assertThat(rec.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);
        assertThat(rec.getOutput(String.class)).isEqualTo("YES");
    }

    @Test
    void run_shouldExecuteElseBranchWhenNoConditionMatches() {
        var execManager = new InMemoryExecutionManager();
        var ctx = new ExecutionContext(UUID.randomUUID(), "pipeline-if", null, null, execManager, null);

        ConstantOperation yesOp = new ConstantOperation("yes-op", "YES");
        ConstantOperation noOp = new ConstantOperation("no-op", "NO");

        Condition<String> containsYes = (input, context) -> input.contains("yes");

        UnaryIfElseContainerDefinition.Builder<String> builder =
                new UnaryIfElseContainerDefinition.Builder<>();

        UnaryIfElseContainerDefinition<String> container =
                builder.conditionally(yesOp, containsYes)
                       .elseOp(noOp);

        OperationExecutionRecord rec = container.run("nothing here", ctx);

        assertThat(rec.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);
        assertThat(rec.getOutput(String.class)).isEqualTo("NO");
    }
}
