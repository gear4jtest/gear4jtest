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
        assertThat(configuration.maxGeneratedSourceBytes()).isEqualTo(4L * 1024L * 1024L);
        assertThat(configuration.maxCompilationOutputBytes()).isEqualTo(8L * 1024L * 1024L);
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
        assertThatThrownBy(() -> new GeneratedCompilationConfiguration(Duration.ofSeconds(1), 1, 1, 0L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGeneratedSourceBytes");
        assertThatThrownBy(() -> new GeneratedCompilationConfiguration(Duration.ofSeconds(1), 1, 1, 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCompilationOutputBytes");
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
        assertThat(configuration.withMaxGeneratedSourceBytes(8L))
                .isEqualTo(new GeneratedCompilationConfiguration(Duration.ofSeconds(2), 3, 4, 8L,
                        GeneratedCompilationConfiguration.DEFAULT_MAX_COMPILATION_OUTPUT_BYTES));
        assertThat(configuration.withMaxCompilationOutputBytes(9L))
                .isEqualTo(new GeneratedCompilationConfiguration(Duration.ofSeconds(2), 3, 4,
                        GeneratedCompilationConfiguration.DEFAULT_MAX_GENERATED_SOURCE_BYTES, 9L));
    }
}
