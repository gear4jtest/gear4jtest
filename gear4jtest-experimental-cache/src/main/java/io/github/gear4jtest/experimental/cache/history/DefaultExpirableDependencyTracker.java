package io.github.gear4jtest.experimental.cache.history;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultExpirableDependencyTracker implements ExpirableDependencyTracker {
    private final Map<String, Instant> consumedExpiries = new ConcurrentHashMap<>();
    private final Set<String> missingExpiryKeys = ConcurrentHashMap.newKeySet();

    @Override
    public void recordConsumed(String key, Instant expiresAt) {
        if (key == null || expiresAt == null) {
            return;
        }

        consumedExpiries.merge(key, expiresAt, (left, right) -> left.isBefore(right) ? left : right);
    }

    @Override
    public void recordMissingExpiry(String key) {
        if (key != null) {
            missingExpiryKeys.add(key);
        }
    }

    @Override
    public boolean isCacheable() {
        return missingExpiryKeys.isEmpty();
    }

    @Override
    public List<String> getMissingExpiryKeys() {
        return List.copyOf(missingExpiryKeys);
    }

    @Override
    public Optional<Instant> minExpiry() {
        Instant min = null;
        for (Instant expiry : consumedExpiries.values()) {
            if (min == null || expiry.isBefore(min)) {
                min = expiry;
            }
        }
        return Optional.ofNullable(min);
    }
}
