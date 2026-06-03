package io.github.gear4jtest.core.model;

import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleEventBusTest {
    @Test
    void hasAsyncReactions_shouldBeTrueWhenSubscriptionsAreDeclared() {
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(Event.class, event -> {
                })).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor).build())
                .build();

        assertThat(definition.hasAsyncReactions()).isTrue();
        assertThat(definition.getSubscriptions()).hasSize(1);
    }

    @Test
    void hasAsyncReactions_shouldBeFalseWhenNothingIsConfigured() {
        EventHandlingDefinition definition = EventHandlingDefinition.builder().build();

        assertThat(definition.hasAsyncReactions()).isFalse();
        assertThat(definition.getSubscriptions()).isEmpty();
        assertThat(definition.getSideComputers()).isEmpty();
    }

    @Test
    void runtimeConfiguration_shouldUseSharedExecutorByDefault() {
        EventHandlingDefinition.RuntimeConfiguration.ExecutorHandle first = EventHandlingDefinition.RuntimeConfiguration
                .builder().build().acquireReactionExecutor();
        EventHandlingDefinition.RuntimeConfiguration.ExecutorHandle second = EventHandlingDefinition.RuntimeConfiguration
                .builder().build().acquireReactionExecutor();

        assertThat(first.shutdownOnClose()).isFalse();
        assertThat(second.shutdownOnClose()).isFalse();
        assertThat(first.executorService()).isSameAs(second.executorService());
    }
}
