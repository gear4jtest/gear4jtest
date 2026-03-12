package io.github.gear4jtest.core.event;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests de la logique de reflection dans EventListener.isAcceptable(...).
 */
class EventListenerTest {

    static class StartedEventListener implements EventListener<OperationStartedEvent> {
        @Override
        public void handleEvent(OperationStartedEvent e) {
            // no-op
        }
    }

    static class GenericEventListener implements EventListener<Event> {
        @Override
        public void handleEvent(Event e) {
            // no-op
        }
    }

    @Test
    void isAcceptable_shouldReturnTrueForMatchingSubtype() {
        EventListener<OperationStartedEvent> listener = new StartedEventListener();
        OperationStartedEvent event =
                new OperationStartedEvent("pipe", UUID.randomUUID(), "op", "input");

        boolean acceptable = listener.isAcceptable(event);

        assertThat(acceptable).isTrue();
    }

    @Test
    void isAcceptable_shouldReturnFalseForNonMatchingSubtype() {
        EventListener<OperationStartedEvent> listener = new StartedEventListener();
        OperationCompletedEvent otherEvent =
                new OperationCompletedEvent("pipe", UUID.randomUUID(), "op", "in", "out");

        boolean acceptable = listener.isAcceptable(otherEvent);

        assertThat(acceptable).isFalse();
    }

    @Test
    void isAcceptable_shouldWorkWithGenericEventListener() {
        UUID executionId = UUID.randomUUID();
        EventListener<Event> listener = new GenericEventListener();
        Event event = new Event("pipe", executionId, "GENERIC");

        boolean acceptable = listener.isAcceptable(event);

        assertThat(acceptable).isTrue();
    }
}
