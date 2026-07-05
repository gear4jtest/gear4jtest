package io.github.gear4jtest.experimental.cache.history.sidecompute;

import java.time.Instant;

public record SideComputeDependency(String key, Instant expiresAt) {}
