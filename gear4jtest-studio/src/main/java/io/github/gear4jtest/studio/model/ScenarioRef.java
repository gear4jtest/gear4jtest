package io.github.gear4jtest.studio.model;

import java.util.UUID;

/** Pinned scenario provenance, safe for receipts, audit and observations. */
public record ScenarioRef(UUID scenarioId, UUID revisionId, long revision) {
    public ScenarioRef {
        java.util.Objects.requireNonNull(scenarioId);
        java.util.Objects.requireNonNull(revisionId);
        if (revision < 1)
            throw new IllegalArgumentException("Invalid scenario revision");
    }
}
