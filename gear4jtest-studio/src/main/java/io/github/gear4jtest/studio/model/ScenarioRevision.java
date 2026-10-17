package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

public record ScenarioRevision(UUID scenarioId,
                               UUID id,
                               long number,
                               String author,
                               Instant savedAt,
                               ScenarioData data) {
    public ScenarioRef reference() {
        return new ScenarioRef(scenarioId, id, number);
    }

    @Override
    public String toString() {
        return "ScenarioRevision[id=" + id + ", number=" + number + "]";
    }
}
