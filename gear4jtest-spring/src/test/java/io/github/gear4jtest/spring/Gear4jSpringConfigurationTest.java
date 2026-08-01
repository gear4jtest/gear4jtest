package io.github.gear4jtest.spring;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
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

    @Test
    void spring_engine_shouldApplyOptionalDependenciesAndContextCustomizers() {
        // Given
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            SequencedIdGenerator idGenerator = new SequencedIdGenerator();
            context.registerBean(ParentContextGuardOperator.class, ParentContextGuardOperator::new);
            context.registerBean(ContextSnapshotOperator.class, ContextSnapshotOperator::new);
            context.registerBean(IdGenerator.class, () -> idGenerator);
            context.registerBean(TaskFactory.class, TaskFactory::new);
            context.registerBean(PayloadCloner.class, IdentityPayloadCloner::new);
            context.registerBean(Gear4jAssemblyLineExecutorCustomizer.class, () -> builder -> builder
                    .parallelExecutionConfiguration(
                                                    ParallelExecutionConfiguration
                                                            .withDefaultAwaitTimeout(Duration.ofSeconds(2)))
                    .workerConcurrencyConfiguration(WorkerConcurrencyConfiguration.defaults())
                    .initialRunContextPolicy(ContextPropagationPolicy.includeKeys("kept"))
                    .nestedRunContextPropagationPolicy(ContextPropagationPolicy.none()));
            context.register(Gear4jSpringConfiguration.class);
            context.refresh();

            AssemblyLine<String, Map<String, Object>> child = AssemblyLines.<String>createAssemblyLine("child-context")
                    .then(Stations.processingOperation("snapshot", ContextSnapshotOperator.class).build())
                    .build();
            AssemblyLine<String, Map<String, Object>> parent = AssemblyLines
                    .<String>createAssemblyLine("parent-context")
                    .then(Stations.processingOperation("guard-parent-context", ParentContextGuardOperator.class)
                            .build())
                    .then(AssemblyLineCallStation.nestedRun("call-child-context", child))
                    .build();

            // When
            ExecutionResult<Map<String, Object>> result = context.getBean(AssemblyLineExecutor.class)
                    .execute(parent, RunRequest.builder()
                            .context(Map.of("kept", "visible", "dropped", "hidden"))
                            .input("payload")
                            .build());

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExecution().getId()).isEqualTo(new UUID(0L, 1L));
            assertThat(result.getResult())
                    .as("nested runs should receive the customized empty propagation policy")
                    .isEmpty();
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

    public static final class ParentContextGuardOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            assertThat(operationExecution.getGlobalContext().snapshotContext())
                    .as("top-level runs should receive only customized context keys")
                    .containsExactly(Map.entry("kept", "visible"));
            return input;
        }
    }

    public static final class ContextSnapshotOperator implements Operator<String, Map<String, Object>> {
        @Override
        public Map<String, Object> transform(String input, StationExecutionContext operationExecution) {
            return operationExecution.getGlobalContext().snapshotContext();
        }
    }

    private static final class SequencedIdGenerator implements IdGenerator {
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public UUID generate() {
            return new UUID(0L, sequence.incrementAndGet());
        }
    }

    private static final class IdentityPayloadCloner implements PayloadCloner {
        @Override
        public <T> T clonePayload(T payload) {
            return payload;
        }
    }
}
