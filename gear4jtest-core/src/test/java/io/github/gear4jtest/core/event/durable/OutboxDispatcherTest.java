package io.github.gear4jtest.core.event.durable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.event.transport.EventEnvelope;
import io.github.gear4jtest.core.event.transport.ExternalEventTransport;
import io.github.gear4jtest.core.event.transport.PublishResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {
    @Test
    void dispatchOnce_shouldMarkRejectedPublicationAsRetryableBeforeMaxAttempts() throws Exception {
        // Given
        DurableEventEnvelopeStore store = mock(DurableEventEnvelopeStore.class);
        ExternalEventTransport transport = mock(ExternalEventTransport.class);
        StoredEventEnvelope stored = stored("event-1", 0);
        when(store.claimPending("consumer", 10, Duration.ofSeconds(30))).thenReturn(List.of(stored));
        when(transport.publish(stored.envelope())).thenReturn(PublishResult.rejected("temporary rejection"));
        OutboxDispatchPolicy policy = OutboxDispatchPolicy.builder()
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(10))
                .maxBackoff(Duration.ofSeconds(1))
                .build();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, transport, "consumer", 10,
                Duration.ofSeconds(30), policy);

        // When
        int dispatched = dispatcher.dispatchOnce();

        // Then
        assertThat(dispatched).isZero();
        verify(store).markFailed(eq("event-1"), any(IllegalStateException.class), eq(true),
                                 eq(Duration.ofMillis(10)));
    }

    @Test
    void dispatchOnce_shouldMarkFailureAsTerminalWhenMaxAttemptsIsReached() throws Exception {
        // Given
        DurableEventEnvelopeStore store = mock(DurableEventEnvelopeStore.class);
        ExternalEventTransport transport = mock(ExternalEventTransport.class);
        StoredEventEnvelope stored = stored("event-1", 2);
        when(store.claimPending("consumer", 10, Duration.ofSeconds(30))).thenReturn(List.of(stored));
        when(transport.publish(stored.envelope())).thenThrow(new IllegalStateException("poison event"));
        OutboxDispatchPolicy policy = OutboxDispatchPolicy.builder()
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(10))
                .maxBackoff(Duration.ofSeconds(1))
                .build();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, transport, "consumer", 10,
                Duration.ofSeconds(30), policy);

        // When
        int dispatched = dispatcher.dispatchOnce();

        // Then
        assertThat(dispatched).isZero();
        verify(store).markFailed(eq("event-1"), any(IllegalStateException.class), eq(false), eq(Duration.ZERO));
    }

    @Test
    void dispatchOnce_shouldMarkAcceptedPublicationAsPublished() throws Exception {
        // Given
        DurableEventEnvelopeStore store = mock(DurableEventEnvelopeStore.class);
        ExternalEventTransport transport = mock(ExternalEventTransport.class);
        StoredEventEnvelope stored = stored("event-1", 0);
        when(store.claimPending("consumer", 10, Duration.ofSeconds(30))).thenReturn(List.of(stored));
        when(transport.publish(stored.envelope())).thenReturn(PublishResult.accepted("transport-1"));
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, transport, "consumer", 10, Duration.ofSeconds(30));

        // When
        int dispatched = dispatcher.dispatchOnce();

        // Then
        assertThat(dispatched).isEqualTo(1);
        verify(store).markPublished("event-1", "transport-1");
    }

    private static StoredEventEnvelope stored(String storeId, int attemptCount) {
        return new StoredEventEnvelope(storeId, envelope(), DurableEventStatus.CLAIMED, attemptCount, Instant.now(),
                Instant.now().plusSeconds(30));
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(UUID.randomUUID(), "TestEvent", "pipeline", UUID.randomUUID(), UUID.randomUUID(),
                "operation", null, "item", Instant.now(), Map.of(), new byte[] { 1 }, "application/json",
                "partition", "1");
    }
}
