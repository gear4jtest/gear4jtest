package io.github.gear4jtest.studio.model;

import java.util.List;

public record ValidationReport(String catalogFingerprint, List<Diagnostic> diagnostics) {
    public ValidationReport {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean valid() {
        return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }
}
