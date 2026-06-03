package io.github.gear4jtest.core.event.durable;

import java.time.Instant;
import java.util.Objects;

import io.github.gear4jtest.core.event.transport.EventEnvelope;

/** Envelope stored in a durable outbox with dispatch metadata. */
public record StoredEventEnvelope(String storeId,
                                  EventEnvelope envelope,
                                  DurableEventStatus status,
                                  int attemptCount,
                                  Instant availableAt,
                                  Instant claimedUntil) {
    public StoredEventEnvelope {
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
