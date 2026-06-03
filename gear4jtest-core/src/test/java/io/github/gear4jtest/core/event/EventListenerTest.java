package io.github.gear4jtest.core.event;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventListenerTest {
    @Test
    void accepts_shouldReturnTrueWhenEventMatchesTypeAndPredicate() {
        EventSubscription<StationFinishedEvent> subscription = EventSubscription
                .on(StationFinishedEvent.class,
                    event -> event.getOperationId().equals("step-1") && event.isSuccessful(), event -> {
                    });

        StationFinishedEvent event = new StationFinishedEvent("pipe", UUID.randomUUID(), UUID.randomUUID(), "step-1",
                null, "item-1", "input", io.github.gear4jtest.core.model.StationLogStatus.SUCCEEDED, "output", null);

        assertThat(subscription.accepts(event)).isTrue();
    }

    @Test
    void accepts_shouldReturnFalseWhenEventTypeDoesNotMatch() {
        EventSubscription<StationFinishedEvent> subscription = EventSubscription.on(StationFinishedEvent.class,
                                                                                    event -> {
                                                                                    });

        Event other = new Event("pipe", UUID.randomUUID());

        assertThat(subscription.accepts(other)).isFalse();
    }

    @Test
    void handle_shouldCastAndForwardTypedEvent() throws Exception {
        AtomicReference<ParameterResolvedEvent> seen = new AtomicReference<>();

        EventSubscription<ParameterResolvedEvent> subscription = EventSubscription.on(ParameterResolvedEvent.class,
                                                                                      seen::set);

        ParameterResolvedEvent event = new ParameterResolvedEvent("pipe", UUID.randomUUID(), UUID.randomUUID(),
                "step-1", null, "item-1", "customer-param", false, String.class.getName());

        subscription.handle(event);

        assertThat(seen.get()).isSameAs(event);
    }
}
