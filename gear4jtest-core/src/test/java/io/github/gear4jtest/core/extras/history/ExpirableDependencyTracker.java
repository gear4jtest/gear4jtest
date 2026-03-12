package io.github.gear4jtest.core.extras.history;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpirableDependencyTracker {

    void recordConsumed(String key, Instant expiresAt);

    void recordMissingExpiry(String key);

    boolean isCacheable();

    List<String> getMissingExpiryKeys();

    Optional<Instant> minExpiry();
}
