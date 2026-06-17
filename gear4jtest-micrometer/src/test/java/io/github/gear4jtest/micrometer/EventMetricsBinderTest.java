package io.github.gear4jtest.micrometer;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMetricsBinderTest {
    @Test
    void bind_shouldExposeEventRuntimeStats() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EventManager manager = new EventManager(EventHandlingDefinition.builder().build(),
                new ExecutionContextRegistry());

        // When
        EventMetricsBinder.bind(meterRegistry, manager);

        // Then
        assertThat(meterRegistry.get("gear4j.events.published").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.events.dispatched").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.events.dropped").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.events.queued").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.events.queue.remaining.capacity").gauge().value()).isPositive();
        assertThat(meterRegistry.get("gear4j.reactions.submitted").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.reactions.completed").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.reactions.dropped").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.reactions.failed").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.reactions.pending").gauge().value()).isZero();
        assertThat(meterRegistry.get("gear4j.reactions.in.flight").gauge().value()).isZero();
    }
}
