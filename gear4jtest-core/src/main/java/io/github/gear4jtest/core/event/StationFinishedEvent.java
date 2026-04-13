package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.persistence.StationLog;
import java.util.UUID;

public final class StationFinishedEvent extends StationEvent {

    private final StationLog.Status status;
    private final Object output;
    private final Exception error;

    public StationFinishedEvent(
            String pipelineId,
            UUID executionId,
            UUID stationExecutionId,
            String operationId,
            UUID parentOperationId,
            String itemId,
            Object input,
            StationLog.Status status,
            Object output,
            Exception error) {
        super(pipelineId, executionId, stationExecutionId, operationId, parentOperationId, itemId, input);
        this.status = status;
        this.output = output;
        this.error = error;
    }

    public StationLog.Status getStatus() {
        return status;
    }

    public Object getOutput() {
        return output;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccessful() {
        return status == StationLog.Status.SUCCEEDED;
    }
}
