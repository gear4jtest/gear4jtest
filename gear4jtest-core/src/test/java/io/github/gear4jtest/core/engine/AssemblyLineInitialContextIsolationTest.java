package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineInitialContextIsolationTest {
    @Test
    void independentRuns_shouldAllowDefensiveCopiesOfMutableContextValues() {
        // Given
        List<String> pipelineDefault = new ArrayList<>(List.of("default"));
        AssemblyLine<String, List<String>> pipeline = AssemblyLines.<String>createAssemblyLine("context-isolation")
                .putContext("items", pipelineDefault)
                .then(processingOperation("mutate", MutateItemsContext.class).build())
                .build();
        AssemblyLineEngine engine = AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .initialRunContextPolicy(ContextPropagationPolicy.copyValues((key, value) -> {
                    if (value instanceof List<?> list) {
                        return new ArrayList<>(list);
                    }
                    return value;
                }))
                .build();

        // When
        ExecutionResult<List<String>> first = engine.execute(pipeline, request());
        ExecutionResult<List<String>> second = engine.execute(pipeline, request());

        // Then
        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(first.getResult()).containsExactly("default", "run");
        assertThat(second.getResult()).containsExactly("default", "run");
        assertThat(second.getResult()).isNotSameAs(first.getResult());
        assertThat(pipelineDefault).as("pipeline defaults must not be shared with isolated runs")
                .containsExactly("default");
    }

    private static RunRequest request() {
        return RunRequest.builder()
                .input("input")
                .resourceFactory(reflectiveResourceFactory())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot instantiate test resource " + clazz.getName(), e);
                }
            }
        };
    }

    public static final class MutateItemsContext implements Operator<String, List<String>> {
        @Override
        public List<String> transform(String input, StationExecutionContext operationExecution) {
            @SuppressWarnings("unchecked")
            List<String> items = operationExecution.getGlobalContext().get("items", List.class);
            items.add("run");
            return items;
        }
    }
}
