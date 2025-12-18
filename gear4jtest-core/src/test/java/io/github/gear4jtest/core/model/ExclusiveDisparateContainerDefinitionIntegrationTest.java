package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.StationKind;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveDisparateContainerDefinitionIntegrationTest {

    static class ConstantOp extends AbstractStation<String, String> {

        private final String out;

        ConstantOp(String id, String out) {
            super(id, StationKind.PROCESSING);
            this.out = out;
        }

        @Override
        protected void setUp(String input, ExecutionContext ctx, StationExecutionContext op) {}

        @Override
        protected String doExecute(String input, ExecutionContext ctx, StationExecutionContext op) {
            return out;
        }
    }

//    @Test
//    void container_shouldSelectSingleMatchingBranch_execute_andPersist() {
//        var execManager = new InMemoryExecutionManager();
//        var ctx = new ExecutionContext("excl", null, null, execManager);
//
//        ConstantOp opYes = new ConstantOp("yes", "YES");
//        ConstantOp opNo  = new ConstantOp("no",  "NO");
//
//        Conditional<String> condYes = (in, c) -> in.contains("yes");
//        Conditional<String> condNo  = (in, c) -> in.contains("no");
//
//        ExclusiveDisparateContainerDefinition.Builder<String> b =
//                new ExclusiveDisparateContainerDefinition.Builder<>();
//
//        ExclusiveDisparateContainerDefinition<String> cont =
//                b.conditionally(opYes, condYes)
//                 .conditionally(opNo, condNo)
//                 .build();
//
//        OperationExecutionRecord record = cont.run("my value has yes", ctx);
//
//        assertThat(record.getOutput(String.class)).isEqualTo("YES");
//    }
}
