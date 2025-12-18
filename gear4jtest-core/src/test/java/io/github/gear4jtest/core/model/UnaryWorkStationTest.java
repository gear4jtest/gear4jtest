package io.github.gear4jtest.core.model;

import java.util.UUID;

import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.UnaryWorkStation;
import io.github.gear4jtest.core.persistence.StationLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnaryWorkStationTest {

    static class UpperCaseOperator implements Operator<String, String> {

        @Override
        public String transform(String input,
                                ExecutionContext context,
                                StationExecutionContext operationExecution) {
            return input == null ? null : input.toUpperCase();
        }
    }

    @Test
    void builder_shouldCreateUnaryProcessingDefinitionAndExecuteWithTypedTransformer() {
        // ResourceFactory qui sait instancier le transformer unaire
        ResourceFactory factory = new TestResourceFactory();

        var execManager = new InMemoryExecutionManager();
        var ctx = new ExecutionContext(UUID.randomUUID(), "pipe-unary", null, factory, execManager, null);

        UnaryWorkStation.Builder<String, UpperCaseOperator> builder =
                new UnaryWorkStation.Builder<>();

        UnaryWorkStation<String> def = builder
                .id("unary-op")
                .type(UpperCaseOperator.class)
                .build();

        StationLog rec = def.run("hello", ctx);

        assertThat(rec.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
        assertThat(rec.getOutput(String.class)).isEqualTo("HELLO");
    }

    public static class TestResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            if (clazz.equals(UpperCaseOperator.class)) {
                return (T) new UpperCaseOperator();
            }
            throw new IllegalArgumentException("Unexpected class: " + clazz);
        }
    }
}
