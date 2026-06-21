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

    private static final class SecretService {
    }

    private static final class SafeService {
    }
}
