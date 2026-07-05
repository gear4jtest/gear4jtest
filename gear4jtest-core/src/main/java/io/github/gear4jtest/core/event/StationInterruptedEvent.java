package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class StationInterruptedEvent extends StationEvent {
    private final StationInterruptionReason reason;
    private final String interruptingOperationId;
    private final Exception error;

    public StationInterruptedEvent(String assemblyLineId,
                                   UUID executionId,
                                   UUID stationExecutionId,
                                   String operationId,
                                   UUID parentOperationId,
                                   String branchId,
                                   String itemId,
                                   Object input,
                                   StationInterruptionReason reason,
                                   String interruptingOperationId,
                                   Exception error) {
        super(assemblyLineId, executionId, stationExecutionId, operationId, parentOperationId, branchId, itemId, input);
        this.reason = reason;
        this.interruptingOperationId = interruptingOperationId;
        this.error = error;
    }

    public StationInterruptionReason getReason() {
        return reason;
    }

    public String getInterruptingOperationId() {
        return interruptingOperationId;
    }

    public Exception getError() {
        return error;
    }
}
