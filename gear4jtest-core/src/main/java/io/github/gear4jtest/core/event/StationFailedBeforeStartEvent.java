package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class StationFailedBeforeStartEvent extends StationEvent {
    private final Exception error;

    public StationFailedBeforeStartEvent(String assemblyLineId,
                                         UUID executionId,
                                         UUID stationExecutionId,
                                         String operationId,
                                         UUID parentOperationId,
                                         String branchId,
                                         String itemId,
                                         Object input,
                                         Exception error) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, branchId, itemId, input);
        this.error = error;
    }

    public Exception getError() {
        return error;
    }
}
