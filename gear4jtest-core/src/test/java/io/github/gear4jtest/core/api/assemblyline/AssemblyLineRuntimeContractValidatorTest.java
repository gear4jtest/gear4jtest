package io.github.gear4jtest.core.api.assemblyline;

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
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineRuntimeContractValidatorTest {
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
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.inlineConfigless();

        EventHandlingDefinition eventHandling = EventHandlingDefinition.builder().build();
        List<RuntimeExtension> extensions = List.of();

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, eventHandling, extensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configless");
    }

    @Test
    void validateConfigurationCoherence_shouldRejectPersistenceWithInlineContract() {
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirement(RuntimeRequirement.defaultEventHandling())
                .build();

        PersistenceConfiguration persistence = PersistenceConfiguration.builder().build();
        List<RuntimeExtension> extensions = List.of();

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, persistence, null, extensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persistence");
    }

    @Test
    void validateConfigurationCoherence_shouldRequireEventHandlingRequirementForInlineEventHandling() {
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        EventHandlingDefinition eventHandling = EventHandlingDefinition.builder().build();
        List<RuntimeExtension> extensions = List.of();

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, eventHandling, extensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-handling requirement");
    }

    @Test
    void validateConfigurationCoherence_shouldRejectNestedRunExtensionsForInlineContracts() {
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        List<RuntimeExtension> runInterceptorExtensions = List.of(new TestRunInterceptor());

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null, runInterceptorExtensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime extension");

        List<RuntimeExtension> runLifecycleExtensions = List.of(new TestRunLifecycle());

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null, runLifecycleExtensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime extension");

        List<RuntimeExtension> executorWrapperExtensions = List.of(new TestExecutorWrapper());

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null, executorWrapperExtensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime extension");
    }

    @Test
    void validateConfigurationCoherence_shouldRequireStationExtensionRequirementForInlineContracts() {
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        List<RuntimeExtension> stationWrapperExtensions = List.of(new TestStationWrapper());

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null, stationWrapperExtensions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required runtime requirement");

        AssemblyLineRuntimeContract satisfied = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirement(RuntimeRequirement.stationExtension(TestStationLifecycle.class))
                .build();

        AssemblyLineRuntimeContractValidator.validateConfigurationCoherence(satisfied, null, null,
                                                                            List.of(new TestStationLifecycle()));
    }

    @Test
    void validateConfigurationCoherence_shouldHonorCustomExtensionInlineMetadata() {
        AssemblyLineRuntimeContract contract = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .build();

        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null,
                                                                List.of(new CustomNestedRunExtension())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires NESTED_RUN");

        RuntimeRequirement requirement = RuntimeRequirement.custom("custom-inline-safe");
        assertThatThrownBy(() -> validateConfigurationCoherence(contract, null, null,
                                                                List.of(new CustomRequirementExtension(requirement))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custom-inline-safe");

        AssemblyLineRuntimeContract satisfied = AssemblyLineRuntimeContract.builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirement(requirement)
                .build();
        validateConfigurationCoherence(satisfied, null, null, List.of(new CustomRequirementExtension(requirement)));
    }

    private static void validateConfigurationCoherence(AssemblyLineRuntimeContract contract,
                                                       PersistenceConfiguration persistenceConfiguration,
                                                       EventHandlingDefinition eventHandlingDefinition,
                                                       List<RuntimeExtension> extensions) {
        AssemblyLineRuntimeContractValidator.validateConfigurationCoherence(contract, persistenceConfiguration,
                                                                            eventHandlingDefinition, extensions);
    }

    private static final class TestRunInterceptor implements RunInterceptorExtension {
        @Override
        public <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                        RunRequest<IN> request,
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

    private static final class CustomNestedRunExtension implements RuntimeExtension {
        @Override
        public boolean requiresNestedRun() {
            return true;
        }
    }

    private record CustomRequirementExtension(RuntimeRequirement requiredInlineRequirement)
            implements RuntimeExtension {}
}
