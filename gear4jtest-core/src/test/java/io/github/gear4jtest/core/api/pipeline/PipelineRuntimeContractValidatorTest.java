package io.github.gear4jtest.core.api.pipeline;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineRuntimeContractValidatorTest {
    @Test
    void defaultConfiguration_shouldProduceInlineConfiglessContract() {
        AssemblyLine.Configuration configuration = AssemblyLine.Configuration.builder().build();

        assertThat(configuration.getRuntimeContract().getInlinePolicy())
                .isEqualTo(InlinePolicy.ALLOWED_WHEN_CONFIGLESS);
    }

    @Test
    void runtimeConfiguration_shouldDefaultToNestedRunOnlyContract() {
        AssemblyLine.Configuration configuration = AssemblyLine.Configuration.builder()
                .persistence(PersistenceConfiguration.builder().build())
                .build();

        assertThat(configuration.getRuntimeContract().getInlinePolicy())
                .isEqualTo(InlinePolicy.ALWAYS_FORBIDDEN);
    }

    @Test
    void validateConfigurationCoherence_shouldRejectConfiglessInlineContractWhenRuntimeConfigIsPresent() {
        PipelineRuntimeContract contract = PipelineRuntimeContract.inlineConfigless();

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null,
                                                                                                 EventHandlingDefinition
                                                                                                         .builder()
                                                                                                         .build(),
                                                                                                 List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configless");
    }

    @Test
    void validateConfigurationCoherence_shouldRejectPersistenceWithInlineContract() {
        PipelineRuntimeContract contract = PipelineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirement(RuntimeRequirement.defaultEventHandling())
                .build();

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 PersistenceConfiguration
                                                                                                         .builder()
                                                                                                         .build(),
                                                                                                 null, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persistence");
    }

    @Test
    void validateConfigurationCoherence_shouldRequireEventHandlingRequirementForInlineEventHandling() {
        PipelineRuntimeContract contract = PipelineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null,
                                                                                                 EventHandlingDefinition
                                                                                                         .builder()
                                                                                                         .build(),
                                                                                                 List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-handling requirement");
    }

    @Test
    void validateConfigurationCoherence_shouldRejectRunScopedExtensionsForInlineContracts() {
        PipelineRuntimeContract contract = PipelineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null, null,
                                                                                                 List.of(new TestRunInterceptor())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run-scoped extension");

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null, null,
                                                                                                 List.of(new TestRunLifecycle())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run-scoped extension");

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null, null,
                                                                                                 List.of(new TestExecutorWrapper())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("executor wrapper extension");
    }

    @Test
    void validateConfigurationCoherence_shouldRequireStationExtensionRequirementForInlineContracts() {
        PipelineRuntimeContract contract = PipelineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        assertThatThrownBy(() -> PipelineRuntimeContractValidator.validateConfigurationCoherence(contract,
                                                                                                 null, null,
                                                                                                 List.of(new TestStationWrapper())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mandatory runtime requirement");

        PipelineRuntimeContract satisfied = PipelineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirement(RuntimeRequirement.stationExtension(TestStationLifecycle.class))
                .build();

        PipelineRuntimeContractValidator.validateConfigurationCoherence(satisfied, null, null,
                                                                        List.of(new TestStationLifecycle()));
    }

    private static final class TestRunInterceptor implements RunInterceptorExtension {
        @Override
        public <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                        RunRequest request,
                                                        ExecutionContext ctx,
                                                        RunChain<IN, OUT> chain) {
            return chain.proceed();
        }
    }

    private static final class TestRunLifecycle implements RunLifecycleExtension {
    }

    private static final class TestExecutorWrapper implements ExecutorWrapperExtension {
        @Override
        public ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx) {
            return delegate;
        }
    }

    private static final class TestStationWrapper implements StationWrapperExtension {
        @Override
        public StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
            return delegate;
        }
    }

    private static final class TestStationLifecycle implements StationLifecycleExtension {
    }
}
