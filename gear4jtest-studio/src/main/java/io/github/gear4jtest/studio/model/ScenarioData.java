package io.github.gear4jtest.studio.model;

/**
 * Stored exactly after admission; never replace a refused fixture with redacted
 * text.
 */
public record ScenarioData(ScenarioInput input, ScenarioExpectation expected, SimulationProfile simulation) {
    public ScenarioData(ScenarioInput input, ScenarioExpectation expected) {
        this(input, expected, null);
    }

    public ScenarioData {
        java.util.Objects.requireNonNull(input);
    }
}
