package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class StationCancelledEvent extends StationEvent {
    private final StationCancellationReason reason;
    private final Exception error;

    public StationCancelledEvent(String assemblyLineId,
                                 UUID executionId,
                                 UUID stationExecutionId,
                                 String operationId,
                                 UUID parentOperationId,
                                 String branchId,
                                 String itemId,
                                 Object input,
                                 StationCancellationReason reason,
                                 Exception error) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, branchId, itemId, input);
        this.reason = reason;
        this.error = error;
    }

    public StationCancellationReason getReason() {
        return reason;
    }

    public Exception getError() {
        return error;
    }
}
