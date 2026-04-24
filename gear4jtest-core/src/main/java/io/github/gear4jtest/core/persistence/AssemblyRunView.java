package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Objects;

public class AssemblyRunView {

    private final AssemblyRunRecord summary;
    private final List<StationLogRecord> rootOperations;

    public AssemblyRunView(AssemblyRunRecord summary, List<StationLogRecord> rootOperations) {
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
        this.rootOperations = List.copyOf(rootOperations == null ? List.of() : rootOperations);
    }

    public AssemblyRunRecord getSummary() {
        return summary;
    }

    public List<StationLogRecord> getRootOperations() {
        return rootOperations;
    }
}
