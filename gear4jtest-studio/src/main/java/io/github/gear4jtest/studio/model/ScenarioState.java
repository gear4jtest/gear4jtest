package io.github.gear4jtest.studio.model;

import java.util.Set;

public record ScenarioState(StudioScenario scenario, ScenarioRevision revision, Set<String> actions) {
    public ScenarioState {
        actions = Set.copyOf(actions);
    }
}
