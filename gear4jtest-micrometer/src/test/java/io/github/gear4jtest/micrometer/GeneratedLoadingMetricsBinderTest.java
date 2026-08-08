package io.github.gear4jtest.micrometer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.external.api.AssemblyLineManager;
import io.github.gear4jtest.external.api.GeneratedCompilationStats;
import io.github.gear4jtest.external.api.GeneratedLoadingPhase;
import io.github.gear4jtest.external.api.GeneratedLoadingPhaseStats;
import io.github.gear4jtest.external.api.GeneratedLoadingStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedLoadingMetricsBinderTest {
    @Test
    void bind_shouldExposeGeneratedRuntimeMetricsWithOnlyFiniteTags() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AssemblyLineManager manager = mock(AssemblyLineManager.class);
        Map<GeneratedLoadingPhase, GeneratedLoadingPhaseStats> phases = Map.of(
                                                                               GeneratedLoadingPhase.ARTIFACT_READ,
                                                                               new GeneratedLoadingPhaseStats(4L, 2L,
                                                                                       40L, 20L),
                                                                               GeneratedLoadingPhase.TRANSLATION,
                                                                               new GeneratedLoadingPhaseStats(2L, 1L,
                                                                                       30L, 22L),
                                                                               GeneratedLoadingPhase.INJECTION,
                                                                               new GeneratedLoadingPhaseStats(1L, 1L,
                                                                                       10L, 10L));
        when(manager.loadingStats()).thenReturn(new GeneratedLoadingStats(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L,
                9, 10, 11, 120L, 60L, 40L, 30L, 20L, 10L, 2L, phases, false));
        when(manager.compilationStats()).thenReturn(new GeneratedCompilationStats(11L, 12L, 13L, 14L, 15L,
                16L, 17L, 18L, 19L, 20, 21L, 22, 23, 24, 140L, 70L, false));

        // When
        GeneratedLoadingMetricsBinder.bind(meterRegistry, manager);

        // Then
        assertThat(meterRegistry.getMeters()).hasSize(50).allSatisfy(meter -> assertThat(meter.measure())
                .allSatisfy(measurement -> assertThat(measurement.getValue()).isFinite()));
        assertThat(meterRegistry.get("gear4j.generated.loading.cache.requests")
                .tag("result", "hit").functionCounter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("gear4j.generated.loading.loads")
                .tag("outcome", "timeout").functionCounter().count()).isEqualTo(7.0d);
        assertThat(meterRegistry.get("gear4j.generated.loading.artifact.integrity.failures")
                .functionCounter().count()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("gear4j.generated.loading.phase.duration")
                .tag("phase", "artifact_read").functionTimer().count()).isEqualTo(4L);
        assertThat(meterRegistry.get("gear4j.generated.loading.phase.duration")
                .tag("phase", "artifact_read").functionTimer().totalTime(TimeUnit.NANOSECONDS)).isEqualTo(40.0d);
        assertThat(meterRegistry.get("gear4j.generated.loading.phase.failures")
                .tag("phase", "injection").functionCounter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("gear4j.generated.compilations")
                .tag("outcome", "limit_rejected").functionCounter().count()).isEqualTo(19.0d);
        assertThat(meterRegistry.get("gear4j.generated.compilation.executor.queued").gauge().value())
                .isEqualTo(24.0d);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("phase", "outcome", "result"));
            assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getValue())
                    .doesNotContain("customer-pipeline", "secret exception message");
        });
        assertThat(meterRegistry.getMeters())
                .extracting(meter -> meter.getId().getTags().stream().map(tag -> tag.getKey()).toList())
                .allSatisfy(keys -> assertThat(Set.copyOf(keys)).isSubsetOf(Set.of("phase", "outcome", "result")));
    }
}
