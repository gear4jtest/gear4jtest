package io.github.gear4jtest.core.event.durable;

import java.time.Duration;
import java.util.Objects;

import io.github.gear4jtest.core.event.transport.ExternalEventTransport;
import io.github.gear4jtest.core.event.transport.PublishResult;

/**
 * Pulls claimed durable events and forwards them to an external transport.
 *
 * <p>
 * This dispatcher provides at-least-once delivery when paired with a durable
 * store and idempotent consumers. It does not promise exactly-once delivery.
 * </p>
 *
 * <p>
 * This is an experimental durable-event building block, not a complete broker.
 * A production store should implement claim ownership, retry scheduling,
 * dead-letter visibility and idempotency guidance explicitly.
 * </p>
 */
public final class OutboxDispatcher {
    private final DurableEventEnvelopeStore store;
    private final ExternalEventTransport transport;
    private final String consumerId;
    private final int batchSize;
    private final Duration claimTtl;
    private final OutboxDispatchPolicy dispatchPolicy;

    public OutboxDispatcher(DurableEventEnvelopeStore store,
                            ExternalEventTransport transport,
                            String consumerId,
                            int batchSize,
                            Duration claimTtl) {
        this(store, transport, consumerId, batchSize, claimTtl, OutboxDispatchPolicy.defaults());
    }

    public OutboxDispatcher(DurableEventEnvelopeStore store,
                            ExternalEventTransport transport,
                            String consumerId,
                            int batchSize,
                            Duration claimTtl,
                            OutboxDispatchPolicy dispatchPolicy) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.consumerId = Objects.requireNonNull(consumerId, "consumerId must not be null");
        this.batchSize = positive(batchSize, "batchSize");
        this.claimTtl = positive(claimTtl, "claimTtl");
        this.dispatchPolicy = Objects.requireNonNull(dispatchPolicy, "dispatchPolicy must not be null");
    }

    public int dispatchOnce() {
        store.releaseExpiredClaims();
        int dispatched = 0;
        for (StoredEventEnvelope stored : store.claimPending(consumerId, batchSize, claimTtl)) {
            try {
                PublishResult result = transport.publish(stored.envelope());
                if (result.accepted()) {
                    store.markPublished(stored.storeId(), result.transportMessageId());
                    dispatched++;
                } else {
                    handleFailure(stored, new IllegalStateException(result.detail()));
                }
            } catch (Exception exception) {
                handleFailure(stored, exception);
            }
        }
        return dispatched;
    }

    private void handleFailure(StoredEventEnvelope stored, Throwable failure) {
        int attemptsAfterFailure = stored.attemptCount() + 1;
        boolean retryable = dispatchPolicy.shouldRetry(failure, attemptsAfterFailure);
        Duration retryDelay = retryable ? dispatchPolicy.retryDelay(attemptsAfterFailure) : Duration.ZERO;
        store.markFailed(stored.storeId(), failure, retryable, retryDelay);
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }
}
