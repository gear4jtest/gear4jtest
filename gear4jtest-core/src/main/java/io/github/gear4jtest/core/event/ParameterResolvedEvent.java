package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class ParameterResolvedEvent extends Event {
    private final UUID stationExecutionId;
    private final String operationId;
    private final UUID parentOperationId;
    private final String itemId;
    private final String parameterDescriptor;
    private final boolean cacheHit;
    private final String valueType;

    public ParameterResolvedEvent(String assemblyLineId,
                                  UUID executionId,
                                  UUID stationExecutionId,
                                  String operationId,
                                  UUID parentOperationId,
                                  String itemId,
                                  String parameterDescriptor,
                                  boolean cacheHit,
                                  String valueType) {
        super(assemblyLineId, executionId);
        this.stationExecutionId = stationExecutionId;
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.itemId = itemId;
        this.parameterDescriptor = parameterDescriptor;
        this.cacheHit = cacheHit;
        this.valueType = valueType;
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

    public String getParameterDescriptor() {
        return parameterDescriptor;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public String getValueType() {
        return valueType;
    }
}
