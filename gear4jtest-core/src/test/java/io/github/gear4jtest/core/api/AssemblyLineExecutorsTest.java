package io.github.gear4jtest.core.api;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineExecutorsTest {
    @Test
    void publicBuilder_shouldCreateAndExecuteDefaultRuntimeWithoutInternalImports() {
        ResourceFactory resources = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                if (type == AppendOperator.class) {
                    return type.cast(new AppendOperator());
                }
                return null;
            }
        };
        AssemblyLine<String, String> pipeline = AssemblyLines.<String>createAssemblyLine("public-executor")
                .then(Stations.processingOperation("append", AppendOperator.class).build())
                .build();

        AssemblyLineExecutor executor = AssemblyLineExecutors.builder()
                .resourceFactory(resources)
                .build();
        ExecutionResult<String> result = executor.execute(pipeline, RunRequest.builder().input("value").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("value-ok");
    }

    public static final class AppendOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-ok";
        }
    }
}
