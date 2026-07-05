package io.github.gear4jtest.experimental.cache.history.sidecompute;

import java.time.Instant;
import java.util.Optional;

public interface SideComputeExpiryRegistry {
    void registerExpiry(String key, Instant expiresAt);

    Optional<Instant> findExpiry(String key);
}
