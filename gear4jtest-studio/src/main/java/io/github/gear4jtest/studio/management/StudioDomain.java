package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.UUID;

public record StudioDomain(UUID id,
                           String key,
                           String name,
                           String description,
                           String owner,
                           long version,
                           boolean archived,
                           Instant createdAt,
                           Instant updatedAt)
        implements ManagedResource {
    @Override
    public ResourceKind kind() {
        return ResourceKind.DOMAIN;
    }

    @Override
    public boolean inheritAccess() {
        return false;
    }
}
