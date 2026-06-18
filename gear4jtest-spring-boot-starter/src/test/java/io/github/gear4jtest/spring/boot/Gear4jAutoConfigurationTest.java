package io.github.gear4jtest.spring.boot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.spring.boot.actuate.Gear4jActuatorAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Gear4jAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Gear4jAutoConfiguration.class,
                                                     Gear4jActuatorAutoConfiguration.class));

    @Test
    void should_create_core_runtime_beans_by_default() {
        // Given / When / Then
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Gear4jProperties.class);
            assertThat(context).hasSingleBean(ExecutionContextRegistry.class);
            assertThat(context).hasSingleBean(PipelineEngine.class);
            assertThat(context).doesNotHaveBean(DatabaseExecutionManager.class);
        });
    }

    @Test
    void boot_engine_should_execute_nested_pipeline_calls_with_default_runtime_strategies() {
        // Given / When / Then
        contextRunner.withBean(AppendBangOperator.class, AppendBangOperator::new)
                .run(context -> {
                    AssemblyLine<String, String> child = ElementModelBuilders.<String>createAssemblyLine("child")
                            .then(ElementModelBuilders.processingOperation("append", AppendBangOperator.class)
                                    .build())
                            .build();
                    AssemblyLine<String, String> parent = ElementModelBuilders.<String>createAssemblyLine("parent")
                            .then(PipelineCallStation.nestedRun("call-child", child))
                            .build();

                    ExecutionResult<String> result = context.getBean(PipelineEngine.class)
                            .execute(parent, RunRequest.builder().input("hello").build());

                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.getResult()).isEqualTo("hello!");
                });
    }

    @Test
    void boot_parallel_default_await_timeout_should_be_applied_to_runtime_strategies() {
        // Given
        ExecutorService branchExecutor = Executors.newSingleThreadExecutor();
        try {
            contextRunner.withBean(SlowOperator.class, SlowOperator::new)
                    .withPropertyValues("gear4j.parallel.default-await-timeout=20ms")
                    .run(context -> {
                        var slowStation = ElementModelBuilders
                                .<String, String, SlowOperator>processingOperation("slow", SlowOperator.class)
                                .build();
                        ContainerBaseStation<String, String> container = new ContainerBaseStation.Builder<String, String>(
                                branchExecutor)
                                .withSubLine("slow-branch", slowStation)
                                .returns(value -> value);
                        AssemblyLine<String, String> pipeline = ElementModelBuilders
                                .<String>createAssemblyLine("parallel-timeout")
                                .then(container)
                                .build();

                        // When
                        ExecutionResult<String> result = context.getBean(PipelineEngine.class)
                                .execute(pipeline, RunRequest.builder().input("hello").build());

                        // Then
                        assertThat(result.isCancelled()).isTrue();
                    });
        } finally {
            branchExecutor.shutdownNow();
        }
    }

    @Test
    void should_create_micrometer_extension_when_registry_is_available() {
        // Given / When / Then
        contextRunner.withBean(SimpleMeterRegistry.class)
                .run(context -> assertThat(context).hasSingleBean(Gear4jMicrometerExtension.class));
    }

    @Test
    void should_create_database_execution_manager_when_persistence_is_enabled() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> assertThat(context).hasSingleBean(DatabaseExecutionManager.class));
    }

    @Test
    void should_fail_fast_when_redaction_mode_requires_redactor_but_none_is_available() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2",
                                    "gear4j.persistence.redaction-mode=REQUIRE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("gear4j.persistence.redaction-mode=REQUIRE requires a "
                                    + "SensitiveDataRedactor bean when persistence is enabled");
                });
    }

    @Test
    void should_create_database_execution_manager_when_redaction_mode_requires_existing_redactor() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(SensitiveDataRedactor.class, () -> (target, value) -> value)
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2",
                                    "gear4j.persistence.redaction-mode=REQUIRE")
                .run(context -> assertThat(context).hasSingleBean(DatabaseExecutionManager.class));
    }

    @Test
    void should_create_persistence_health_indicator_when_actuator_and_persistence_are_available() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> assertThat(context).hasBean("gear4jPersistenceHealthIndicator")
                        .getBean("gear4jPersistenceHealthIndicator")
                        .isInstanceOf(HealthIndicator.class));
    }

    @Test
    void should_fail_fast_when_persistence_is_enabled_without_dialect() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("gear4j.persistence.dialect is required when persistence is enabled");
                });
    }

    public static final class AppendBangOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "!";
        }
    }

    public static final class SlowOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return input;
        }
    }
}
