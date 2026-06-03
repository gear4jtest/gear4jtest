package io.github.gear4jtest.core.event.durable;

import java.time.Duration;
import java.util.List;

import io.github.gear4jtest.core.event.transport.EventEnvelope;

/**
 * Durable store contract for outbox-style event publication.
 *
 * <p>
 * This SPI is intentionally separate from the in-memory {@code EventManager}.
 * Implementations should persist envelopes before they are considered accepted
 * and must expect at-least-once dispatch semantics.
 * </p>
 */
public interface DurableEventEnvelopeStore {
    StoredEventEnvelope append(EventEnvelope envelope);

    List<StoredEventEnvelope> claimPending(String consumerId, int maxMessages, Duration claimTtl);

    void markPublished(String storeId, String transportMessageId);

    void markFailed(String storeId, Throwable failure, boolean retryable);

    void releaseExpiredClaims();
}
