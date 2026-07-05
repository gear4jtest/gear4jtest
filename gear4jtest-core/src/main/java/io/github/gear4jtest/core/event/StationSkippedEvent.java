package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class StationSkippedEvent extends StationEvent {
    private final StationSkipReason reason;

    public StationSkippedEvent(String assemblyLineId,
                               UUID executionId,
                               UUID stationExecutionId,
                               String operationId,
                               UUID parentOperationId,
                               String branchId,
                               String itemId,
                               Object input,
                               StationSkipReason reason) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, branchId, itemId, input);
        this.reason = reason;
    }

    public StationSkipReason getReason() {
        return reason;
    }
}
