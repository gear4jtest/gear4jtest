package io.github.gear4jtest.core.api.util;

import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine.Configuration;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineRuntimeContract;
import io.github.gear4jtest.core.api.assemblyline.RuntimeRequirement;

/**
 * Builders and shortcuts for assembly-line runtime contracts.
 */
public final class RuntimeContracts {
    private RuntimeContracts() {
    }

    public static Configuration.Builder configuration() {
        return new Configuration.Builder();
    }

    public static AssemblyLineRuntimeContract.Builder runtimeContract() {
        return AssemblyLineRuntimeContract.builder();
    }

    public static AssemblyLineRuntimeContract inlineConfigless() {
        return AssemblyLineRuntimeContract.inlineConfigless();
    }

    public static AssemblyLineRuntimeContract nestedRunOnly() {
        return AssemblyLineRuntimeContract.nestedRunOnly();
    }

    public static AssemblyLineRuntimeContract inlineWhenRequirementsSatisfied(List<RuntimeRequirement> requirements) {
        return AssemblyLineRuntimeContract.inlineWhenRequirementsSatisfied(requirements);
    }
}
