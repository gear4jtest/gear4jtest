package io.github.gear4jtest.studio.model;

/**
 * Stored exactly after admission; never replace a refused fixture with redacted
 * text.
 */
public record ScenarioData(ScenarioInput input, ScenarioExpectation expected) {
    public ScenarioData {
        java.util.Objects.requireNonNull(input);
    }
}
