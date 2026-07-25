package io.github.gear4jtest.external.api;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedCompilationConfigurationTest {
    @Test
    void defaults_shouldProvideFiniteBoundedPolicy() {
        GeneratedCompilationConfiguration configuration = GeneratedCompilationConfiguration.defaults();

        assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.maxConcurrentCompilations()).isEqualTo(1);
        assertThat(configuration.queueCapacity()).isEqualTo(32);
    }

    @Test
    void constructor_shouldRejectInvalidValues() {
        assertThatThrownBy(() -> new GeneratedCompilationConfiguration(Duration.ZERO, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new GeneratedCompilationConfiguration(Duration.ofSeconds(1), 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentCompilations");
        assertThatThrownBy(() -> new GeneratedCompilationConfiguration(Duration.ofSeconds(1), 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueCapacity");
    }

    @Test
    void withMethods_shouldPreserveOtherValues() {
        GeneratedCompilationConfiguration configuration = new GeneratedCompilationConfiguration(Duration.ofSeconds(2),
                3, 4);

        assertThat(configuration.withTimeout(Duration.ofSeconds(5)))
                .isEqualTo(new GeneratedCompilationConfiguration(Duration.ofSeconds(5), 3, 4));
        assertThat(configuration.withMaxConcurrentCompilations(6))
                .isEqualTo(new GeneratedCompilationConfiguration(Duration.ofSeconds(2), 6, 4));
        assertThat(configuration.withQueueCapacity(7))
                .isEqualTo(new GeneratedCompilationConfiguration(Duration.ofSeconds(2), 3, 7));
    }
}
