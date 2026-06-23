package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public class OperationBaseEvent extends Event {
    private final String operationId;
    private final Object input;
    private Object output;

    public OperationBaseEvent(String assemblyLineId,
                              UUID executionId,
                              String type,
                              String operationId,
                              Object input,
                              Object output) {
        super(assemblyLineId, executionId, type);
        this.operationId = operationId;
        this.input = input;
        this.output = output;
    }

    public OperationBaseEvent(String assemblyLineId, UUID executionId, String type, String operationId, Object input) {
        super(assemblyLineId, executionId, type);
        this.operationId = operationId;
        this.input = input;
    }

    public String getOperationId() {
        return operationId;
    }

    public Object getInput() {
        return input;
    }

    public Object getOutput() {
        return output;
    }
}
