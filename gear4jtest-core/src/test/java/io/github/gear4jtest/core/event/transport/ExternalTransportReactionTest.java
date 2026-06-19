package io.github.gear4jtest.core.event.transport;

import java.time.Instant;
import java.util.UUID;

import io.github.gear4jtest.core.event.Event;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalTransportReactionTest {
    @Test
    void handle_shouldPublishMappedEnvelope() throws Exception {
        Event event = new Event("pipeline", UUID.randomUUID(), "TYPE");
        EventEnvelope envelope = envelope();
        var captured = new java.util.concurrent.atomic.AtomicReference<EventEnvelope>();
        ExternalTransportReaction<Event> reaction = new ExternalTransportReaction<>(ignored -> envelope, published -> {
            captured.set(published);
            return PublishResult.accepted("msg-1");
        });

        reaction.handle(event);

        assertThat(captured).hasValue(envelope);
    }

    @Test
    void handle_shouldWrapMapperFailure() {
        Event event = new Event("pipeline", UUID.randomUUID(), "TYPE");
        ExternalTransportReaction<Event> reaction = new ExternalTransportReaction<>(ignored -> {
            throw new IllegalStateException("mapping failed");
        }, ignored -> PublishResult.accepted("msg-1"));

        assertThatThrownBy(() -> reaction.handle(event))
                .isInstanceOf(ExternalTransportPublishException.class)
                .hasMessage("Failed to map runtime event to external transport envelope.")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_shouldWrapTransportFailure() {
        ExternalTransportReaction<Event> reaction = new ExternalTransportReaction<>(ignored -> envelope(), ignored -> {
            throw new IllegalStateException("transport down");
        });

        assertThatThrownBy(() -> reaction.handle(new Event("pipeline", UUID.randomUUID(), "TYPE")))
                .isInstanceOf(ExternalTransportPublishException.class)
                .hasMessage("Failed to publish event envelope to external transport.")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_shouldFailWhenTransportRejectsEnvelope() {
        ExternalTransportReaction<Event> reaction = new ExternalTransportReaction<>(ignored -> envelope(),
                ignored -> PublishResult.rejected("queue-full"));

        assertThatThrownBy(() -> reaction.handle(new Event("pipeline", UUID.randomUUID(), "TYPE")))
                .isInstanceOf(ExternalTransportPublishException.class)
                .hasMessage("External transport rejected event envelope: queue-full");
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(UUID.randomUUID(), "TYPE", "pipeline", UUID.randomUUID(), null, null, null, null,
                Instant.now(), java.util.Map.of(), new byte[] { 1 }, "application/json", "pipeline", "1");
    }
}
