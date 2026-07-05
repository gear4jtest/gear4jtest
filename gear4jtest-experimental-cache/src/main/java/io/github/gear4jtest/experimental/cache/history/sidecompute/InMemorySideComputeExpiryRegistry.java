package io.github.gear4jtest.experimental.cache.history.sidecompute;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySideComputeExpiryRegistry implements SideComputeExpiryRegistry {
    private final Map<String, Instant> expiries = new ConcurrentHashMap<>();

    @Override
    public void registerExpiry(String key, Instant expiresAt) {
        if (key == null || expiresAt == null) {
            return;
        }
        expiries.put(key, expiresAt);
    }

    @Override
    public Optional<Instant> findExpiry(String key) {
        return Optional.ofNullable(expiries.get(key));
    }
}
