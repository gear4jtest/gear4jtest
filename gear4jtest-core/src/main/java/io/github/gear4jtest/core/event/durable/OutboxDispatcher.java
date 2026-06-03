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
 */
public final class OutboxDispatcher {
    private final DurableEventEnvelopeStore store;
    private final ExternalEventTransport transport;
    private final String consumerId;
    private final int batchSize;
    private final Duration claimTtl;

    public OutboxDispatcher(DurableEventEnvelopeStore store,
                            ExternalEventTransport transport,
                            String consumerId,
                            int batchSize,
                            Duration claimTtl) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.consumerId = Objects.requireNonNull(consumerId, "consumerId must not be null");
        this.batchSize = positive(batchSize, "batchSize");
        this.claimTtl = positive(claimTtl, "claimTtl");
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
                    store.markFailed(stored.storeId(), new IllegalStateException(result.detail()), true);
                }
            } catch (Exception exception) {
                store.markFailed(stored.storeId(), exception, true);
            }
        }
        return dispatched;
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
