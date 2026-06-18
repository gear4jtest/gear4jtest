package io.github.gear4jtest.core.persistence;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.AbstractAssemblyRunState;

public class AssemblyRun extends AbstractAssemblyRunState {
    public AssemblyRun() {
        super();
    }

    public AssemblyRun(UUID id, String pipelineId, Map<String, Object> pipelineParams) {
        super(id, pipelineId, pipelineParams);
    }

    public static AssemblyRun childOf(AssemblyRun parent, String assemblyLineId) {
        AssemblyRun child = new AssemblyRun();
        child.setId(UUID.randomUUID());
        child.setPipelineId(assemblyLineId);
        child.setParentExecutionId(parent.getId());
        child.setRootExecutionId(parent.getRootExecutionId() != null ? parent.getRootExecutionId() : parent.getId());
        child.markStarted();
        return child;
    }
}
