package io.github.gear4jtest.core.event;

import java.util.UUID;

public final class StationStartedEvent extends StationEvent {

    public StationStartedEvent(
            String pipelineId,
            UUID executionId,
            UUID stationExecutionId,
            String operationId,
            UUID parentOperationId,
            String itemId,
            Object input) {
        super(pipelineId, executionId, stationExecutionId, operationId, parentOperationId, itemId, input);
    }
}
