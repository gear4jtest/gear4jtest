package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnaryProcessingOperationDefinitionTest {

    static class UpperCaseTransformer implements Transformer<String, String> {

        @Override
        public String transform(String input,
                                ExecutionContext context,
                                OperationExecutionContext operationExecution) {
            return input == null ? null : input.toUpperCase();
        }
    }

    @Test
    void builder_shouldCreateUnaryProcessingDefinitionAndExecuteWithTypedTransformer() {
        // ResourceFactory qui sait instancier le transformer unaire
        ResourceFactory factory = new TestResourceFactory();

        var execManager = new InMemoryExecutionManager();
        var ctx = new ExecutionContext("pipe-unary", null, factory, execManager);

        UnaryProcessingOperationDefinition.Builder<String, UpperCaseTransformer> builder =
                new UnaryProcessingOperationDefinition.Builder<>();

        UnaryProcessingOperationDefinition<String> def = builder
                .id("unary-op")
                .type(UpperCaseTransformer.class)
                .build();

        OperationExecutionRecord rec = def.run("hello", ctx);

        assertThat(rec.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);
        assertThat(rec.getOutput(String.class)).isEqualTo("HELLO");
    }

    public static class TestResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            if (clazz.equals(UpperCaseTransformer.class)) {
                return (T) new UpperCaseTransformer();
            }
            throw new IllegalArgumentException("Unexpected class: " + clazz);
        }
    }
}
