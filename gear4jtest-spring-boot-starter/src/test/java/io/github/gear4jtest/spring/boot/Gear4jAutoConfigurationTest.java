package io.github.gear4jtest.spring.boot;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.security.RedactionTarget;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.jdbc.execution.DatabaseExecutionManager;
import io.github.gear4jtest.jdbc.persistence.PersistenceJsonCodec;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.spring.boot.actuate.Gear4jActuatorAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

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
            assertThat(context).hasSingleBean(AssemblyLineExecutor.class);
            assertThat(context).doesNotHaveBean(DatabaseExecutionManager.class);
        });
    }

    @Test
    void boot_engine_should_execute_nested_pipeline_calls_with_default_runtime_strategies() {
        // Given / When / Then
        contextRunner.withBean(AppendBangOperator.class, AppendBangOperator::new)
                .run(context -> {
                    AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                            .then(Stations.processingOperation("append", AppendBangOperator.class)
                                    .build())
                            .build();
                    AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                            .then(AssemblyLineCallStation.nestedRun("call-child", child))
                            .build();

                    ExecutionResult<String> result = context.getBean(AssemblyLineExecutor.class)
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
                        var slowStation = Stations
                                .<String, String, SlowOperator>processingOperation("slow", SlowOperator.class)
                                .build();
                        ContainerBaseStation<String, String> container = new ContainerBaseStation.Builder<String, String>(
                                branchExecutor)
                                .id("parallel-container")
                                .withBranch("slow-branch", slowStation)
                                .returns(results -> results.get("slow-branch", String.class));
                        AssemblyLine<String, String> pipeline = AssemblyLines
                                .<String>createAssemblyLine("parallel-timeout")
                                .then(container)
                                .build();

                        // When
                        ExecutionResult<String> result = context.getBean(AssemblyLineExecutor.class)
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
                .run(context -> {
                    assertThat(context).hasSingleBean(Gear4jMicrometerExtension.class);
                    assertThat(context).hasSingleBean(Gear4jAutoConfiguration.Gear4jEventMetricsRegistrar.class);
                    assertThat(context.getBean(SimpleMeterRegistry.class)
                            .find("gear4j.events.process.active.runtimes").gauge()).isNotNull();
                });
    }

    @Test
    void should_create_database_execution_manager_when_persistence_is_enabled() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> assertThat(context).hasSingleBean(DatabaseExecutionManager.class));
    }

    @Test
    void should_use_application_object_mapper_for_jdbc_persistence() {
        // Given
        ObjectMapper applicationObjectMapper = new ObjectMapper();

        // When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(ObjectMapper.class, () -> applicationObjectMapper)
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);
                    Object repository = ReflectionTestUtils.getField(manager, "repository");
                    Object jsonCodec = ReflectionTestUtils.getField(repository, "jsonCodec");

                    assertThat(ReflectionTestUtils.getField(jsonCodec, "objectMapper"))
                            .isSameAs(applicationObjectMapper);
                });
    }

    @Test
    void should_prefer_explicit_persistence_json_codec_over_application_object_mapper() {
        // Given
        PersistenceJsonCodec jsonCodec = mock(PersistenceJsonCodec.class);

        // When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(PersistenceJsonCodec.class, () -> jsonCodec)
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);
                    Object repository = ReflectionTestUtils.getField(manager, "repository");

                    assertThat(ReflectionTestUtils.getField(repository, "jsonCodec")).isSameAs(jsonCodec);
                });
    }

    @Test
    void should_use_application_payload_cloner_for_persistence_snapshots() {
        // Given
        PayloadCloner payloadCloner = mock(PayloadCloner.class);

        // When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(PayloadCloner.class, () -> payloadCloner)
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);

                    assertThat(ReflectionTestUtils.getField(manager, "payloadCloner")).isSameAs(payloadCloner);
                });
    }

    @Test
    void should_discard_sensitive_values_by_default_when_persistence_is_enabled() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);
                    SensitiveDataRedactor redactor = (SensitiveDataRedactor) ReflectionTestUtils.getField(manager,
                                                                                                          "redactor");

                    assertThat(SensitiveDataRedactor.isDiscardingSensitiveValues(redactor)).isTrue();
                    assertThat(redactor.redact(RedactionTarget.RUN_INPUT, "secret-input")).isNull();
                    assertThat(redactor.redact(RedactionTarget.RUN_RESULT, "secret-result")).isNull();
                    assertThat(redactor.redact(RedactionTarget.RUN_ERROR_MESSAGE, "secret-error")).isNull();
                    assertThat(redactor.redact(RedactionTarget.RUN_CONTEXT, Map.of("token", "secret")))
                            .isEqualTo(Map.of());
                });
    }

    @Test
    void should_allow_raw_capture_when_explicitly_disabled() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2",
                                    "gear4j.persistence.redaction-mode=DISABLED")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);
                    SensitiveDataRedactor redactor = (SensitiveDataRedactor) ReflectionTestUtils.getField(manager,
                                                                                                          "redactor");

                    assertThat(redactor.redact(RedactionTarget.RUN_INPUT, "secret-input"))
                            .isEqualTo("secret-input");
                    assertThat(redactor.redact(RedactionTarget.RUN_CONTEXT, Map.of("token", "secret")))
                            .isEqualTo(Map.of("token", "secret"));
                });
    }

    @SuppressWarnings("removal")
    @Test
    void should_keep_explicit_warn_mode_as_deprecated_raw_capture_compatibility() {
        // Given / When
        SensitiveDataRedactor redactor = Gear4jAutoConfiguration.resolveRedactor(null,
                                                                                 Gear4jProperties.RedactionMode.WARN);

        // Then
        assertThat(SensitiveDataRedactor.isNone(redactor)).isTrue();
        assertThat(redactor.redact(RedactionTarget.RUN_INPUT, "secret-input")).isEqualTo("secret-input");
    }

    @Test
    void should_use_explicit_redactor_with_safe_default_mode() {
        // Given
        SensitiveDataRedactor customRedactor = (target, value) -> "redacted";

        // When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(SensitiveDataRedactor.class, () -> customRedactor)
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    DatabaseExecutionManager manager = context.getBean(DatabaseExecutionManager.class);
                    assertThat(ReflectionTestUtils.getField(manager, "redactor")).isSameAs(customRedactor);
                });
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
    void should_reject_builtin_noop_redactor_when_redaction_is_required() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(SensitiveDataRedactor.class, SensitiveDataRedactor::none)
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
                .withBean(SensitiveDataRedactor.class, () -> (target, value) -> "redacted")
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2",
                                    "gear4j.persistence.redaction-mode=REQUIRE")
                .run(context -> assertThat(context).hasSingleBean(DatabaseExecutionManager.class));
    }

    @Test
    void should_create_persistence_liveness_and_readiness_indicators_whenPersistenceIsAvailable() {
        // Given / When / Then
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2")
                .run(context -> {
                    assertThat(context).hasBean("gear4jPersistenceReadinessIndicator")
                            .hasBean("gear4jPersistenceHealthIndicator")
                            .hasBean("gear4jPersistenceLivenessIndicator");
                    assertThat(context.getBean("gear4jPersistenceReadinessIndicator"))
                            .isSameAs(context.getBean("gear4jPersistenceHealthIndicator"))
                            .isInstanceOf(HealthIndicator.class);
                    assertThat(context.getBean("gear4jPersistenceLivenessIndicator"))
                            .isInstanceOf(HealthIndicator.class);
                });
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
