package io.github.gear4jtest.core.execution.trace;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.internal.AbstractAssemblyRunState;

public class AssemblyRunTrace extends AbstractAssemblyRunState implements RunTrace {
    public AssemblyRunTrace() {
        super();
    }

    public AssemblyRunTrace(UUID id, String assemblyLineId, Map<String, Object> pipelineParams) {
        super(id, assemblyLineId, pipelineParams);
    }

    public static AssemblyRunTrace childOf(AssemblyRunTrace parent, String assemblyLineId) {
        AssemblyRunTrace child = new AssemblyRunTrace();
        child.setId(UUID.randomUUID());
        child.setAssemblyLineId(assemblyLineId);
        child.setParentExecutionId(parent.getId());
        child.setRootExecutionId(parent.getRootExecutionId() != null ? parent.getRootExecutionId() : parent.getId());
        child.markStarted();
        return child;
    }
}
