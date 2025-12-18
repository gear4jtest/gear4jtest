package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.StationKind;

import static org.assertj.core.api.Assertions.assertThat;

class UnaryContainerDefinitionIntegrationTest {

    static class AppendOp extends AbstractStation<String, String> {

        private final String suffix;

        AppendOp(String id, String suffix) {
            super(id, StationKind.PROCESSING);
            this.suffix = suffix;
        }

        @Override
        protected void setUp(String input, ExecutionContext c, StationExecutionContext op) {}

        @Override
        protected String doExecute(String input, ExecutionContext c, StationExecutionContext op) {
            return input + suffix;
        }
    }

//    @Test
//    void unaryContainer_shouldWrapSingleOperation_andPassThrough() {
//        var execManager = new InMemoryExecutionManager();
//        var ctx = new ExecutionContext("unary", null, null, execManager);
//
//        UnaryContainerDefinition.Builder<String> builder =
//                new UnaryContainerDefinition.Builder<>();
//
//        UnaryContainerDefinition<String> cont =
//                builder.child(new AppendOp("append", "!")).build();
//
//        OperationExecutionRecord rec = cont.run("Hello", ctx);
//
//        assertThat(rec.getOutput(String.class)).isEqualTo("Hello!");
//    }
}
