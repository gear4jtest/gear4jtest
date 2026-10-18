package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable durable simulation report; isolated from runtime journals and
 * metrics.
 */
public record StudioSimulation(UUID id,
                               UUID scenarioScopeId,
                               UUID targetScopeId,
                               String actor,
                               Instant createdAt,
                               String runtimeContextId,
                               String applicationBuild,
                               ScenarioRef scenario,
                               ScenarioTarget target,
                               UUID definitionRevisionId,
                               int definitionSchemaVersion,
                               String versionArtifactHash,
                               SimulationResult result,
                               List<Diagnostic> diagnostics) {
    public StudioSimulation {
        // Older reports have no diagnostic list. Empty does not certify
        // historical runtime equivalence.
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Override
    public String toString() {
        return "StudioSimulation[id=" + id + ", targetScopeId=" + targetScopeId + "]";
    }

    public StudioSimulation withoutCaptures() {
        return new StudioSimulation(id, scenarioScopeId, targetScopeId, actor, createdAt, runtimeContextId,
                applicationBuild, scenario, target, definitionRevisionId, definitionSchemaVersion,
                versionArtifactHash, result.withoutCaptures(), diagnostics);
    }
}
