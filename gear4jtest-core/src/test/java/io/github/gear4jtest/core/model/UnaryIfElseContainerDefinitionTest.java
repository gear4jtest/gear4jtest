package io.github.gear4jtest.core.model;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.Condition;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.StationKind;
import io.github.gear4jtest.core.model.UnaryIfElseContainerDefinition;
import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.persistence.StationLog;

class UnaryIfElseContainerDefinitionTest {

    static class ConstantOperation extends AbstractStation<String, String> {

        private final String constant;

        ConstantOperation(String id, String constant) {
            super(id, StationKind.PROCESSING);
            this.constant = constant;
        }

        @Override
        protected void setUp(String input, ExecutionContext context, StationExecutionContext operationExecution) {
            // no-op
        }

        @Override
        protected String doExecute(String input,
                                   ExecutionContext context,
                                   StationExecutionContext operationExecution) {
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

        StationLog rec = container.run("this is yes", ctx);

        assertThat(rec.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
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

        StationLog rec = container.run("nothing here", ctx);

        assertThat(rec.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
        assertThat(rec.getOutput(String.class)).isEqualTo("NO");
    }
}
