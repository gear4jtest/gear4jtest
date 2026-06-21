package io.github.gear4jtest.external.api.loader;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.loader.DependencyInjector.InjectionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleDependencyInjectorTest {
    @Test
    void registerBean_shouldExposeBeanToRunModeOnlyByDefault() throws Exception {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        SecretService secretService = new SecretService();
        injector.registerBean("secretService", secretService);

        // When / Then
        var runLine = new GeneratedWithRequiredSecret();
        injector.injectDependencies(runLine, ExecutionMode.RUN);
        assertThat(runLine.secretService).isSameAs(secretService);

        var testLine = new GeneratedWithRequiredSecret();
        assertThatThrownBy(() -> injector.injectDependencies(testLine, ExecutionMode.TEST))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("Bean 'secretService' is not allowed in TEST mode");
    }

    @Test
    void registerBeanWithModes_shouldAllowExplicitTestInjection() throws Exception {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        SafeService safeService = new SafeService();
        injector.registerBean("safeService", safeService, ExecutionMode.TEST, ExecutionMode.RUN);
        var generated = new GeneratedWithRequiredSafeService();

        // When
        injector.injectDependencies(generated, ExecutionMode.TEST);

        // Then
        assertThat(generated.safeService).isSameAs(safeService);
    }

    @Test
    void injectDependencies_shouldSkipOptionalBeanDisallowedForMode() throws Exception {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        injector.registerBean("secretService", new SecretService());
        var generated = new GeneratedWithOptionalSecret();

        // When
        injector.injectDependencies(generated, ExecutionMode.TEST);

        // Then
        assertThat(generated.secretService).isNull();
    }

    @Test
    void injectDependencies_shouldSupportDefaultFieldNameAndIgnoreOptionalMissingBeans() throws Exception {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        SafeService safeService = new SafeService();
        injector.registerBean("safeService", safeService, ExecutionMode.TEST);
        var generated = new GeneratedWithFieldNameAndOptionalMissingBean();

        // When
        injector.injectDependencies(generated, ExecutionMode.TEST);

        // Then
        assertThat(generated.safeService).isSameAs(safeService);
        assertThat(generated.missingService).isNull();
    }

    @Test
    void registerBean_shouldValidateArgumentsAndAllowedModes() {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();

        // When / Then
        assertThatThrownBy(() -> injector.registerBean(null, new SafeService()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
        assertThatThrownBy(() -> injector.registerBean("safeService", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bean must not be null");
        assertThatThrownBy(() -> injector.registerBean("safeService", new SafeService(), (ExecutionMode) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("firstMode must not be null");
        assertThatThrownBy(() -> injector.registerBean("safeService", new SafeService(), java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("allowedModes must not be empty");
        assertThatThrownBy(() -> injector.registerBean("safeService", new SafeService(), ExecutionMode.RUN,
                                                       (ExecutionMode) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("additional mode must not be null");
    }

    @Test
    void injectDependencies_shouldValidateInputsAndReportMissingOrIncompatibleRequiredBeans() {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        injector.registerBean("safeService", "wrong-type", ExecutionMode.TEST);

        // When / Then
        assertThatThrownBy(() -> injector.injectDependencies(null, ExecutionMode.TEST))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("instance must not be null");
        assertThatThrownBy(() -> injector.injectDependencies(new GeneratedWithRequiredSafeService(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
        assertThatThrownBy(() -> injector.injectDependencies(new GeneratedWithRequiredSafeService(),
                                                             ExecutionMode.TEST))
                .isInstanceOf(InjectionException.class)
                .hasMessage("Required bean not found: safeService");
        assertThatThrownBy(() -> injector.injectDependencies(new GeneratedWithRequiredSecret(), ExecutionMode.RUN))
                .isInstanceOf(InjectionException.class)
                .hasMessage("Required bean not found: secretService");
    }

    @Test
    void getBean_shouldValidateArgumentsAndReturnTypedOptional() {
        // Given
        SimpleDependencyInjector injector = new SimpleDependencyInjector();
        SafeService safeService = new SafeService();
        injector.registerBean("safeService", safeService);

        // When / Then
        assertThat(injector.getBean("safeService", SafeService.class)).contains(safeService);
        assertThat(injector.getBean("safeService", SecretService.class)).isEmpty();
        assertThat(injector.getBean("missing", SafeService.class)).isEmpty();
        assertThatThrownBy(() -> injector.getBean(null, SafeService.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
        assertThatThrownBy(() -> injector.getBean("safeService", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type must not be null");
    }

    private static final class GeneratedWithRequiredSecret {
        @Inject("secretService")
        private SecretService secretService;
    }

    private static final class GeneratedWithRequiredSafeService {
        @Inject("safeService")
        private SafeService safeService;
    }

    private static final class GeneratedWithOptionalSecret {
        @Inject(value = "secretService", required = false)
        private SecretService secretService;
    }

    private static final class GeneratedWithFieldNameAndOptionalMissingBean {
        @Inject
        private SafeService safeService;

        @Inject(value = "missingService", required = false)
        private SecretService missingService;
    }

    private static final class SecretService {
    }

    private static final class SafeService {
    }
}
