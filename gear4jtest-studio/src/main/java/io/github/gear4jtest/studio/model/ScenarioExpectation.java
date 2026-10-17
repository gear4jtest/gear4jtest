package io.github.gear4jtest.studio.model;

/**
 * Optional exact String output; evaluated before runtime capture/redaction,
 * without exporting the value.
 */
public record ScenarioExpectation(String outcome, String output) {
    public ScenarioExpectation {
        if (outcome == null
                || !java.util.Set.of("SUCCEEDED", "FAILED", "CANCELLED", "STOPPED", "SKIPPED").contains(outcome)
                || output != null && output.length() > 8192)
            throw new IllegalArgumentException("Invalid scenario expectation");
    }

    @Override
    public String toString() {
        return "ScenarioExpectation[outcome=" + outcome + "]";
    }
}
