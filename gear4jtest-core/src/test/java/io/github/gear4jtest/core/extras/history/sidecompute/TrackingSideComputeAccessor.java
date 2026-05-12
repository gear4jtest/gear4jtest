package io.github.gear4jtest.core.extras.history.sidecompute;

import java.time.Instant;

import io.github.gear4jtest.core.sidecompute.SideComputeAccessor;

/**
 * Decorator that tracks the side-compute values actually consumed by code under
 * test.
 *
 * <p>
 * Expiration is resolved through {@link SideComputeExpiryRegistry}.
 * </p>
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
