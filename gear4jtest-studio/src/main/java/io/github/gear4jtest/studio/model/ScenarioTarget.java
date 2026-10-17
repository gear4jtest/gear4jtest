package io.github.gear4jtest.studio.model;

import java.util.UUID;

/** Exactly one pinned structured draft revision or frozen version. */
public record ScenarioTarget(UUID draftId, Long revision, UUID versionId) {
    public ScenarioTarget {
        if (versionId == null ? draftId == null || revision == null || revision < 1
                : draftId != null || revision != null)
            throw new IllegalArgumentException("Invalid scenario target");
    }
}
