package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.UUID;

import io.github.gear4jtest.studio.model.DraftRevision;

public record StudioDraft(UUID id,
                          UUID chainId,
                          String name,
                          String owner,
                          long version,
                          boolean inheritAccess,
                          boolean abandoned,
                          long head,
                          DraftRevision.Origin origin,
                          UUID baseRevisionId,
                          Instant createdAt,
                          Instant updatedAt)
        implements ManagedResource {
    @Override
    public ResourceKind kind() {
        return ResourceKind.DRAFT;
    }
}
