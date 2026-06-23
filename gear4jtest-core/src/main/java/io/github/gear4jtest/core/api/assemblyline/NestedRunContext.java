package io.github.gear4jtest.core.api.assemblyline;

import java.util.UUID;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

/**
 * Lineage information linking a nested run to the parent station that triggered
 * it.
 */
public record NestedRunContext(UUID parentExecutionId,
                               UUID rootExecutionId,
                               UUID parentStationLogId,
                               String parentAssemblyLineId,
                               String parentStationId) {
    public static NestedRunContext from(StationExecutionContext parentStationContext) {
        AssemblyRunTrace parentRun = parentStationContext.getGlobalContext().getAssemblyLineExecution();
        UUID parentExecutionId = parentRun.getId();
        UUID rootExecutionId = parentRun.getRootExecutionId() != null ? parentRun.getRootExecutionId()
                : parentExecutionId;
        return new NestedRunContext(parentExecutionId, rootExecutionId, parentStationContext.getRecord().getId(),
                parentRun.getAssemblyLineId(), parentStationContext.getOperationId());
    }
}
