package io.github.gear4jtest.core.extras.history.sidecompute;

import java.time.Instant;

public record SideComputeDependency(String key, Instant expiresAt) {
}
