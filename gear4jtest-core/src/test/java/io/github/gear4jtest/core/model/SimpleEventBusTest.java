package io.github.gear4jtest.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class EventHandlingDefinitionTest {

    @Test
    void hasAsyncReactions_shouldBeTrueWhenSubscriptionsAreDeclared() {
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(Event.class, event -> {}))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .build())
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
}
