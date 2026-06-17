package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public abstract class StationEvent extends Event {
    private final UUID stationExecutionId;
    private final String operationId;
    private final UUID parentOperationId;
    private final String branchId;
    private final String itemId;
    private final Object input;

    protected StationEvent(String pipelineId,
                           UUID executionId,
                           UUID stationExecutionId,
                           String operationId,
                           UUID parentOperationId,
                           String itemId,
                           Object input) {
        this(pipelineId, executionId, stationExecutionId, operationId, parentOperationId, null, itemId, input);
    }

    protected StationEvent(String pipelineId,
                           UUID executionId,
                           UUID stationExecutionId,
                           String operationId,
                           UUID parentOperationId,
                           String branchId,
                           String itemId,
                           Object input) {
        super(pipelineId, executionId);
        this.stationExecutionId = stationExecutionId;
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.branchId = branchId;
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

    public String getBranchId() {
        return branchId;
    }

    public String getItemId() {
        return itemId;
    }

    public Object getInput() {
        return input;
    }
}
