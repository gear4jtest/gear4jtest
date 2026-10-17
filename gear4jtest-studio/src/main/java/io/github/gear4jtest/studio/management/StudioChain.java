package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.UUID;

public record StudioChain(UUID id,
                          UUID domainId,
                          String key,
                          String runtimeAssemblyLineId,
                          String name,
                          String description,
                          String owner,
                          long version,
                          boolean inheritAccess,
                          boolean archived,
                          Instant createdAt,
                          Instant updatedAt)
        implements ManagedResource {
    @Override
    public ResourceKind kind() {
        return ResourceKind.CHAIN;
    }
}
