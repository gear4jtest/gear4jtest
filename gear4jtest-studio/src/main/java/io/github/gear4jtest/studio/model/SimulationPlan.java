package io.github.gear4jtest.studio.model;

import java.util.List;

/**
 * Declarative substitution plan only. No operator, resource, fixture provider
 * or compiler is called.
 */
public record SimulationPlan(String engineVersion,
                             String catalogFingerprint,
                             List<Diagnostic> diagnostics,
                             List<Substitution> substitutions) {
    public SimulationPlan {
        diagnostics = List.copyOf(diagnostics);
        substitutions = List.copyOf(substitutions);
    }

    public boolean valid() {
        return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    public record Substitution(String nodeId, String capability, String path, SimulationProfile.Behavior behavior) {}
}
