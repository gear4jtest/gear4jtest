package io.github.gear4jtest.micrometer;

import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassLoaderMetricsBinderTest {
    @Test
    void bind_shouldExposeClassLoaderOccupancyEvictionsAndRejectionsWithoutTags() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder()
                .maxLoaders(1)
                .maxBytecodeWeightBytes(10L)
                .build();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("first", loader, null, 4L);
        registry.register("second", loader, null, 4L);
        assertThatThrownBy(() -> registry.register("oversized", loader, null, 11L))
                .isInstanceOf(IllegalStateException.class);

        // When
        ClassLoaderMetricsBinder.bind(meterRegistry, registry);

        // Then
        assertThat(meterRegistry.getMeters()).hasSize(10).allSatisfy(meter -> assertThat(meter.measure())
                .allSatisfy(measurement -> assertThat(measurement.getValue()).isFinite()));
        assertThat(meterRegistry.get("gear4j.generated.classloaders.cached").gauge().value()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("gear4j.generated.classloaders.bytecode.bytes").gauge().value()).isEqualTo(4.0d);
        assertThat(meterRegistry.get("gear4j.generated.classloaders.evictions").functionCounter().count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.get("gear4j.generated.classloaders.rejections").functionCounter().count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }
}
