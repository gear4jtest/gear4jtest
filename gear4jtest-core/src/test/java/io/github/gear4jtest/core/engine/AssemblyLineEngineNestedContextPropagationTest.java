package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineEngineNestedContextPropagationTest {
    @Test
    void nestedRun_shouldInheritAllContextValuesShallowlyByDefault() {
        AssemblyLine<String, String> parent = parentCalling(childReadingStringContext());
        AssemblyLineEngine engine = engine(ContextPropagationPolicy.inheritAllShallow());

        ExecutionResult<String> result = engine.execute(parent, requestWithContext(Map.of("visible", "value")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("value");
    }

    @Test
    void nestedRun_shouldAllowDroppingParentContext() {
        AssemblyLine<String, String> parent = parentCalling(childReadingStringContext());
        AssemblyLineEngine engine = engine(ContextPropagationPolicy.none());

        ExecutionResult<String> result = engine.execute(parent, requestWithContext(Map.of("visible", "value")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("missing");
    }

    @Test
    void nestedRun_shouldAllowDefensiveCopiesOfMutableValues() {
        List<String> parentList = new ArrayList<>(List.of("parent"));
        AssemblyLine<String, List<String>> parent = parentCalling(childMutatingListContext());
        AssemblyLineEngine engine = engine(ContextPropagationPolicy.copyValues((key, value) -> {
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return value;
        }));

        ExecutionResult<List<String>> result = engine.execute(parent, requestWithContext(Map.of("items", parentList)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).containsExactly("parent", "child");
        assertThat(parentList).as("the parent value should not be shared with the child run")
                .containsExactly("parent");
    }

    private static AssemblyLineEngine engine(ContextPropagationPolicy contextPropagationPolicy) {
        return AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .nestedRunContextPropagationPolicy(contextPropagationPolicy)
                .build();
    }

    private static <OUT> AssemblyLine<String, OUT> parentCalling(AssemblyLine<String, OUT> child) {
        return AssemblyLines.<String>createAssemblyLine("parent")
                .then(AssemblyLineCallStation.nestedRun("call-child", child))
                .build();
    }

    private static AssemblyLine<String, String> childReadingStringContext() {
        return AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("read-context", ReadVisibleContext.class).build())
                .build();
    }

    private static AssemblyLine<String, List<String>> childMutatingListContext() {
        return AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("mutate-context", MutateItemsContext.class).build())
                .build();
    }

    private static RunRequest<String> requestWithContext(Map<String, ?> context) {
        return RunRequest.builder()
                .input("input")
                .context(new LinkedHashMap<>(context))
                .resourceFactory(reflectiveResourceFactory())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ReflectiveResourceFactory();
    }

    public static class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class ReadVisibleContext implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return operationExecution.getGlobalContext().find("visible", String.class).orElse("missing");
        }
    }

    public static class MutateItemsContext implements Operator<String, List<String>> {
        @Override
        public List<String> transform(String input, StationExecutionContext operationExecution) {
            @SuppressWarnings("unchecked")
            List<String> items = operationExecution.getGlobalContext().get("items", List.class);
            items.add("child");
            return items;
        }
    }
}
