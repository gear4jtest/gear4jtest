package io.github.gear4jtest.external.api;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedLoadingConfigurationTest {
    @Test
    void defaults_shouldProvideFiniteBoundedPolicy() {
        GeneratedLoadingConfiguration configuration = GeneratedLoadingConfiguration.defaults();

        assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(configuration.maxConcurrentLoads()).isEqualTo(4);
        assertThat(configuration.queueCapacity()).isEqualTo(32);
    }

    @Test
    void constructor_shouldRejectInvalidValues() {
        assertThatThrownBy(() -> new GeneratedLoadingConfiguration(Duration.ZERO, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new GeneratedLoadingConfiguration(Duration.ofSeconds(1), 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentLoads");
        assertThatThrownBy(() -> new GeneratedLoadingConfiguration(Duration.ofSeconds(1), 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueCapacity");
    }

    @Test
    void withMethods_shouldPreserveOtherValues() {
        GeneratedLoadingConfiguration configuration = new GeneratedLoadingConfiguration(Duration.ofSeconds(2), 3,
                4);

        assertThat(configuration.withTimeout(Duration.ofSeconds(5)))
                .isEqualTo(new GeneratedLoadingConfiguration(Duration.ofSeconds(5), 3, 4));
        assertThat(configuration.withMaxConcurrentLoads(6))
                .isEqualTo(new GeneratedLoadingConfiguration(Duration.ofSeconds(2), 6, 4));
        assertThat(configuration.withQueueCapacity(7))
                .isEqualTo(new GeneratedLoadingConfiguration(Duration.ofSeconds(2), 3, 7));
    }
}
