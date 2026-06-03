package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Objects;

public class AssemblyRunDetails {
    private final AssemblyRun summary;
    private final List<StationLog> rootOperations;

    public AssemblyRunDetails(AssemblyRun summary, List<StationLog> rootOperations) {
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
        this.rootOperations = List.copyOf(rootOperations == null ? List.of() : rootOperations);
    }

    public AssemblyRun getSummary() {
        return summary;
    }

    public List<StationLog> getRootOperations() {
        return rootOperations;
    }
}
