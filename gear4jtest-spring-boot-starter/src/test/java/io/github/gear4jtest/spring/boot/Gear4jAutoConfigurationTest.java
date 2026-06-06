package io.github.gear4jtest.spring.boot;

import javax.sql.DataSource;

import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Gear4jAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Gear4jAutoConfiguration.class));

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
}
