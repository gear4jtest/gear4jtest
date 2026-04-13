package io.github.gear4jtest.core.event;

import java.util.UUID;

public abstract class StationEvent extends Event {

    private final UUID stationExecutionId;
    private final String operationId;
    private final UUID parentOperationId;
    private final String itemId;
    private final Object input;

    protected StationEvent(
            String pipelineId,
            UUID executionId,
            UUID stationExecutionId,
            String operationId,
            UUID parentOperationId,
            String itemId,
            Object input) {
        super(pipelineId, executionId);
        this.stationExecutionId = stationExecutionId;
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.itemId = itemId;
        this.input = input;
    }

    public UUID getStationExecutionId() {
        return stationExecutionId;
    }

    public String getOperationId() {
        return operationId;
    }

    public UUID getParentOperationId() {
        return parentOperationId;
    }

    public String getItemId() {
        return itemId;
    }

    public Object getInput() {
        return input;
    }
}
