package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable management identity; version is an optimistic-lock token, not a
 * published Gear4J version.
 */
public sealed interface ManagedResource permits StudioDomain, StudioChain, StudioDraft {
    UUID id();

    String name();

    String owner();

    long version();

    Instant createdAt();

    Instant updatedAt();

    ResourceKind kind();

    boolean inheritAccess();
}
