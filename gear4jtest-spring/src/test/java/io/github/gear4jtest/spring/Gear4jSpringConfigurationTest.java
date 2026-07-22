package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class Gear4jSpringConfigurationTest {
    @Test
    void should_wire_core_spring_beans() {
        // Given
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SampleResource.class, SampleResource::new);
            context.register(Gear4jSpringConfiguration.class);

            // When
            context.refresh();

            // Then
            assertThat(context.getBean(ResourceFactory.class).getResource(SampleResource.class))
                    .as("resource factory should resolve Spring beans")
                    .isSameAs(context.getBean(SampleResource.class));
            assertThat(context.getBean(ExecutionContextRegistry.class))
                    .as("execution context registry")
                    .isNotNull();
            assertThat(context.getBean(AssemblyLineExecutor.class))
                    .as("pipeline engine")
                    .isNotNull();
            assertThat(context.getBean(AssemblyLineRegistry.class).getAll())
                    .as("assembly line registry should be available even when no pipeline beans exist")
                    .isEmpty();
        }
    }

    @Test
    void spring_engine_should_execute_nested_pipeline_calls_with_default_runtime_strategies() {
        // Given
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AppendBangOperator.class, AppendBangOperator::new);
            context.register(Gear4jSpringConfiguration.class);
            context.refresh();

            AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                    .then(Stations.processingOperation("append", AppendBangOperator.class).build())
                    .build();
            AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                    .then(AssemblyLineCallStation.nestedRun("call-child", child))
                    .build();

            // When
            ExecutionResult<String> result = context.getBean(AssemblyLineExecutor.class)
                    .execute(parent, RunRequest.builder().input("hello").build());

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo("hello!");
        }
    }

    static final class SampleResource {
    }

    public static final class AppendBangOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "!";
        }
    }
}
