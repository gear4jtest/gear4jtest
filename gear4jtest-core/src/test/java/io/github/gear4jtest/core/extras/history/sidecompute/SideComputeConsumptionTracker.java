package io.github.gear4jtest.core.extras.history.sidecompute;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SideComputeConsumptionTracker {

    private final Map<String, Instant> consumedExpiries = new ConcurrentHashMap<>();
    private final Set<String> missingExpiryKeys = ConcurrentHashMap.newKeySet();

    public void recordConsumed(String key, Instant expiresAt) {
        if (key == null || expiresAt == null) {
            return;
        }

        consumedExpiries.merge(
                key,
                expiresAt,
                (left, right) -> left.isBefore(right) ? left : right);
    }

    public void recordMissingExpiry(String key) {
        if (key != null) {
            missingExpiryKeys.add(key);
        }
    }

    public boolean isCacheable() {
        return missingExpiryKeys.isEmpty();
    }

    public Set<String> getMissingExpiryKeys() {
        return Collections.unmodifiableSet(missingExpiryKeys);
    }

    public List<SideComputeDependency> snapshot() {
        List<SideComputeDependency> result = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : consumedExpiries.entrySet()) {
            result.add(new SideComputeDependency(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    public Optional<Instant> minExpiry() {
        Instant min = null;
        for (Instant expiry : consumedExpiries.values()) {
            if (min == null || expiry.isBefore(min)) {
                min = expiry;
            }
        }
        return Optional.ofNullable(min);
    }

    public boolean isEmpty() {
        return consumedExpiries.isEmpty();
    }
}