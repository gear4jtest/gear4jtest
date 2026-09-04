package io.github.gear4jtest.core.api.config;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.event.EventSubscription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventHandlingDefinitionTest {
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
        assertThat(definition.getGlobalEventConfiguration().getEventPayloadPolicy()
                .mapStationInput("sensitive-input", null)).isNull();
        assertThat(definition.getGlobalEventConfiguration().getEventPayloadPolicy()
                .mapStationOutput("sensitive-output", null)).isNull();
    }

    @Test
    void eventConfiguration_shouldRequireExplicitOptInForRawPayloads() {
        EventHandlingDefinition.EventConfiguration configuration = EventHandlingDefinition.EventConfiguration
                .builder().eventPayloadPolicy(EventPayloadPolicy.passthrough()).build();

        assertThat(configuration.getEventPayloadPolicy().mapStationInput("input", null)).isEqualTo("input");
        assertThat(configuration.getEventPayloadPolicy().mapStationOutput("output", null)).isEqualTo("output");
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
        assertThat(first.executorService()).isInstanceOfSatisfying(ThreadPoolExecutor.class,
                                                                   executor -> assertThat(executor
                                                                           .allowsCoreThreadTimeOut()).isTrue());
    }

    @Test
    void runtimeConfiguration_shouldUseBoundedEventQueueByDefault() {
        EventHandlingDefinition.RuntimeConfiguration configuration = EventHandlingDefinition.RuntimeConfiguration
                .builder().build();

        assertThat(configuration.getEventQueueCapacity())
                .isEqualTo(EventHandlingDefinition.RuntimeConfiguration.DEFAULT_EVENT_QUEUE_CAPACITY);
    }

    @Test
    void runtimeConfiguration_shouldExposeExplicitShutdownDefaultFactories() {
        EventHandlingDefinition.RuntimeConfiguration waitForDrain = EventHandlingDefinition.RuntimeConfiguration
                .waitForDrainDefaults();
        EventHandlingDefinition.RuntimeConfiguration detachAndDrain = EventHandlingDefinition.RuntimeConfiguration
                .detachAndDrainDefaults();

        assertThat(waitForDrain.getShutdownMode())
                .isEqualTo(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.WAIT_FOR_DRAIN);
        assertThat(detachAndDrain.getShutdownMode())
                .isEqualTo(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN);
        assertThat(detachAndDrain.getDetachCleanupTimeout()).isEqualTo(detachAndDrain.getShutdownTimeout());
    }

    @Test
    void runtimeConfiguration_shouldRejectNonPositiveTimeouts() {
        EventHandlingDefinition.RuntimeConfiguration.Builder zeroShutdownTimeoutBuilder = EventHandlingDefinition.RuntimeConfiguration
                .builder().shutdownTimeout(Duration.ZERO);

        assertThatThrownBy(zeroShutdownTimeoutBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shutdownTimeout");

        EventHandlingDefinition.RuntimeConfiguration.Builder negativeCleanupTimeoutBuilder = EventHandlingDefinition.RuntimeConfiguration
                .builder().detachCleanupTimeout(Duration.ofMillis(-1));

        assertThatThrownBy(negativeCleanupTimeoutBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detachCleanupTimeout");
    }

    @Test
    void runtimeConfiguration_shouldRejectInvalidEventQueueCapacity() {
        EventHandlingDefinition.RuntimeConfiguration.Builder invalidQueueCapacityBuilder = EventHandlingDefinition.RuntimeConfiguration
                .builder().eventQueueCapacity(0);

        assertThatThrownBy(invalidQueueCapacityBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventQueueCapacity must be >= 1");
    }
}
