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
 *
 * <p>
 * This package is still an experimental extension point. The core currently
 * provides the contracts and dispatcher scaffolding, not a full production
 * outbox implementation with a built-in JDBC store.
 * </p>
 */
public interface DurableEventEnvelopeStore {
    StoredEventEnvelope append(EventEnvelope envelope);

    List<StoredEventEnvelope> claimPending(String consumerId, int maxMessages, Duration claimTtl);

    void markPublished(String storeId, String transportMessageId);

    void markFailed(String storeId, Throwable failure, boolean retryable);

    /**
     * Records a failed dispatch attempt and optionally makes the envelope available
     * again after the supplied retry delay.
     *
     * <p>
     * Implementations that do not support delayed retries may ignore the delay and
     * delegate to {@link #markFailed(String, Throwable, boolean)}.
     * </p>
     */
    default void markFailed(String storeId, Throwable failure, boolean retryable, Duration retryDelay) {
        markFailed(storeId, failure, retryable);
    }

    void releaseExpiredClaims();
}
