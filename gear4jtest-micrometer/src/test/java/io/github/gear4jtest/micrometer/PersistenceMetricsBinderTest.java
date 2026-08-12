package io.github.gear4jtest.micrometer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.persistence.PersistenceFlushObservation;
import io.github.gear4jtest.core.persistence.PersistenceFlushObserver;
import io.github.gear4jtest.core.persistence.PersistenceFlushSubscription;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistenceMetricsBinderTest {
    @Test
    void bind_shouldExposeAllPersistenceRuntimeStats() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PersistenceRuntimeMonitor manager = mock(PersistenceRuntimeMonitor.class);
        when(manager.snapshotStats()).thenReturn(new PersistenceRuntimeStats(2, 3, 4, 5, 6, 7,
                Instant.parse("2026-07-12T18:00:00Z"), Duration.ofMillis(2_500), null, null, null, false));

        // When
        PersistenceMetricsBinder.bind(meterRegistry, manager);

        // Then
        assertThat(meterRegistry.get("gear4j.persistence.active.runs").gauge().value()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("gear4j.persistence.buffered.station.logs").gauge().value()).isEqualTo(3.0d);
        assertThat(meterRegistry.get("gear4j.persistence.buffered.station.logs.oldest.age.seconds").gauge().value())
                .isEqualTo(2.5d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.scheduled").gauge().value()).isEqualTo(4.0d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.completed").gauge().value()).isEqualTo(5.0d);
        assertThat(meterRegistry.get("gear4j.persistence.flushes.failed").gauge().value()).isEqualTo(6.0d);
        assertThat(meterRegistry.get("gear4j.persistence.appends.rejected").gauge().value()).isEqualTo(7.0d);
    }

    @Test
    void bind_shouldRecordBoundedFlushDurationDistributionsAndRemoveSubscription() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TestPersistenceRuntimeMonitor manager = new TestPersistenceRuntimeMonitor();

        // When
        PersistenceFlushSubscription subscription = PersistenceMetricsBinder.bindWithSubscription(meterRegistry,
                                                                                                  manager);
        manager.emit(new PersistenceFlushObservation(Duration.ofMillis(125),
                PersistenceFlushObservation.Trigger.ASYNC, PersistenceFlushObservation.Outcome.SUCCEEDED));
        manager.emit(new PersistenceFlushObservation(Duration.ofMillis(40),
                PersistenceFlushObservation.Trigger.EXPLICIT, PersistenceFlushObservation.Outcome.FAILED));

        // Then
        assertThat(meterRegistry.timer("gear4j.persistence.flush.duration", "trigger", "async", "outcome",
                                       "succeeded")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125.0d);
        assertThat(meterRegistry.timer("gear4j.persistence.flush.duration", "trigger", "explicit", "outcome",
                                       "failed")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(40.0d);

        subscription.close();
        manager.emit(new PersistenceFlushObservation(Duration.ofMillis(25),
                PersistenceFlushObservation.Trigger.ASYNC, PersistenceFlushObservation.Outcome.SUCCEEDED));
        assertThat(meterRegistry.timer("gear4j.persistence.flush.duration", "trigger", "async", "outcome",
                                       "succeeded")
                .count()).isEqualTo(1L);
    }

    private static final class TestPersistenceRuntimeMonitor implements PersistenceRuntimeMonitor {
        private PersistenceFlushObserver flushObserver = observation -> {
            // no subscriber
        };

        @Override
        public PersistenceRuntimeStats snapshotStats() {
            return new PersistenceRuntimeStats(0, 0, 0, 0, 0, 0, Instant.EPOCH, Duration.ZERO,
                    null, null, null, false);
        }

        @Override
        public PersistenceFlushSubscription subscribeToFlushes(PersistenceFlushObserver observer) {
            this.flushObserver = observer;
            return () -> this.flushObserver = observation -> {
                // subscription removed
            };
        }

        private void emit(PersistenceFlushObservation observation) {
            flushObserver.onFlush(observation);
        }
    }
}
