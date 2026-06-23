package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class StationStartedEvent extends StationEvent {
    public StationStartedEvent(String assemblyLineId,
                               UUID executionId,
                               UUID stationExecutionId,
                               String operationId,
                               UUID parentOperationId,
                               String itemId,
                               Object input) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, itemId, input);
    }

    public StationStartedEvent(String assemblyLineId,
                               UUID executionId,
                               UUID stationExecutionId,
                               String operationId,
                               UUID parentOperationId,
                               String branchId,
                               String itemId,
                               Object input) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, branchId, itemId, input);
    }
}
