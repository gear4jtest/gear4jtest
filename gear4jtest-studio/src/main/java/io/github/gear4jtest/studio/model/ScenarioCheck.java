package io.github.gear4jtest.studio.model;

import java.util.List;

/**
 * Assertion result, distinct from the Gear4J business outcome; contains no
 * actual/expected payload.
 */
public record ScenarioCheck(Status status, List<String> differences) {
    public enum Status {
        PASSED, FAILED, NOT_REQUESTED, NOT_EVALUATED
    }

    public ScenarioCheck {
        java.util.Objects.requireNonNull(status);
        differences = List.copyOf(differences);
        if (differences.size() > 2 || !java.util.Set.of("OUTCOME_MISMATCH", "OUTPUT_MISMATCH").containsAll(differences))
            throw new IllegalArgumentException("Invalid scenario check");
    }

    public static ScenarioCheck evaluate(ScenarioExpectation expected, String outcome, Object output) {
        if (expected == null)
            return new ScenarioCheck(Status.NOT_REQUESTED, List.of());
        var differences = new java.util.ArrayList<String>();
        if (!expected.outcome().equals(outcome))
            differences.add("OUTCOME_MISMATCH");
        if (expected.output() != null && !expected.output().equals(output))
            differences.add("OUTPUT_MISMATCH");
        return new ScenarioCheck(differences.isEmpty() ? Status.PASSED : Status.FAILED, differences);
    }
}
