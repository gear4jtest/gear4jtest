package io.github.gear4jtest.core.extras.history.sidecompute;

import java.time.Instant;

import io.github.gear4jtest.core.sidecompute.SideComputeAccessor;

/**
 * Décorateur qui track les side-computes réellement consommés.
 *
 * <p>
 * Le TTL est résolu via SideComputeExpiryRegistry (publié par les
 * side-computes).
 */
public final class TrackingSideComputeAccessor implements SideComputeAccessor {

    private final SideComputeAccessor delegate;
    private final SideComputeExpiryRegistry expiryRegistry;
    private final SideComputeConsumptionTracker tracker;

    public TrackingSideComputeAccessor(SideComputeAccessor delegate,
                                       SideComputeExpiryRegistry expiryRegistry,
                                       SideComputeConsumptionTracker tracker) {
        this.delegate = delegate;
        this.expiryRegistry = expiryRegistry;
        this.tracker = tracker;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        T value = delegate.get(key, type);

        Instant expiresAt = expiryRegistry.findExpiry(key).orElse(null);
        if (expiresAt != null) {
            tracker.recordConsumed(key, expiresAt);
        } else {
            tracker.recordMissingExpiry(key);
        }
        return value;
    }

    @Override
    public boolean isPresent(String key) {
        return delegate.isPresent(key);
    }
}
