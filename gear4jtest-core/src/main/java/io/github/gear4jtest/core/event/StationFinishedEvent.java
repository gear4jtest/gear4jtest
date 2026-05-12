package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.model.StationLogStatus;

public final class StationFinishedEvent extends StationEvent {
    private final StationLogStatus status;
    private final Object output;
    private final Exception error;

    public StationFinishedEvent(String pipelineId,
                                UUID executionId,
                                UUID stationExecutionId,
                                String operationId,
                                UUID parentOperationId,
                                String itemId,
                                Object input,
                                StationLogStatus status,
                                Object output,
                                Exception error) {
        super(pipelineId, executionId, stationExecutionId, operationId, parentOperationId, itemId, input);
        this.status = status;
        this.output = output;
        this.error = error;
    }

    public StationLogStatus getStatus() {
        return status;
    }

    public Object getOutput() {
        return output;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccessful() {
        return status == StationLogStatus.SUCCEEDED;
    }
}
