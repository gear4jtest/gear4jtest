package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRuntimeMetricsTest {
    @Test
    void processSnapshot_shouldAggregateRuntimeActivityAndDispatchLatency() {
        // Given
        ProcessEventRuntimeStats before = EventRuntimeMetrics.snapshot();
        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, ignored -> {
            // The process aggregator observes the reaction lifecycle without tags.
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                .shutdownTimeout(Duration.ofSeconds(2)).build()).build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        // When
        manager.publish(new Event("line", UUID.randomUUID(), "aggregated"));
        manager.shutdown();
        ProcessEventRuntimeStats after = EventRuntimeMetrics.snapshot();

        // Then
        assertThat(after.activeRuntimes()).isEqualTo(before.activeRuntimes());
        assertThat(after.queuedEvents()).isEqualTo(before.queuedEvents());
        assertThat(after.inFlightReactions()).isEqualTo(before.inFlightReactions());
        assertThat(after.publishedEvents() - before.publishedEvents()).isEqualTo(1);
        assertThat(after.dispatchedEvents() - before.dispatchedEvents()).isEqualTo(1);
        assertThat(after.submittedReactions() - before.submittedReactions()).isEqualTo(1);
        assertThat(after.completedReactions() - before.completedReactions()).isEqualTo(1);
        assertThat(after.dispatchLatencySamples() - before.dispatchLatencySamples()).isEqualTo(1);
        assertThat(after.maxDispatchLatencyNanos()).isNotNegative();
    }
}
