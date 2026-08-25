package io.github.gear4jtest.micrometer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.external.api.artifact.ArtifactSpoolMonitor;
import io.github.gear4jtest.external.api.artifact.ArtifactSpoolStats;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreMonitor;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactMetricsBinderTest {
    @Test
    void bind_shouldExposeStoreLatencySizeFailuresAndSpoolOccupancyWithBoundedTags() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ArtifactStoreMonitor store = mock(ArtifactStoreMonitor.class);
        when(store.snapshotStats()).thenReturn(new ArtifactStoreStats(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                10L, 11L));
        ArtifactSpoolMonitor spool = mock(ArtifactSpoolMonitor.class);
        when(spool.snapshotSpoolStats()).thenReturn(new ArtifactSpoolStats(12L, 13L, 14L, 15L, 16L, 17L, 18L, 19));

        // When
        ArtifactStoreMetricsBinder.bind(meterRegistry, store);
        ArtifactSpoolMetricsBinder.bind(meterRegistry, spool);

        // Then
        assertThat(meterRegistry.getMeters()).hasSize(18).allSatisfy(meter -> assertThat(meter.measure())
                .isNotEmpty()
                .allSatisfy(measurement -> assertThat(measurement.getValue()).isFinite()));
        assertThat(meterRegistry.get("gear4j.artifacts.store.operations")
                .tags("operation", "read", "outcome", "failed").functionCounter().count()).isEqualTo(8.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.store.bytes")
                .tag("operation", "read").functionCounter().count()).isEqualTo(9.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.store.operation.duration")
                .tag("operation", "read").functionTimer().totalTime(TimeUnit.NANOSECONDS)).isEqualTo(10.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.spool.files").gauge().value()).isEqualTo(12.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.spool.bytes").gauge().value()).isEqualTo(13.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.spool.instances").gauge().value()).isEqualTo(19.0d);
        assertThat(meterRegistry.get("gear4j.artifacts.spool.quota.rejections").functionCounter().count())
                .isEqualTo(17.0d);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("operation", "outcome"));
            assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getValue())
                    .allSatisfy(value -> assertThat(value)
                            .isIn("read", "write", "completed", "failed", "closed_early"));
        });
        assertThat(meterRegistry.getMeters())
                .extracting(meter -> meter.getId().getTags().stream().map(tag -> tag.getKey()).toList())
                .allSatisfy(keys -> assertThat(Set.copyOf(keys)).isSubsetOf(Set.of("operation", "outcome")));
    }
}
