package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public class OperationCompletedEvent extends OperationBaseEvent {
    private static final String TYPE = "OPERATION_COMPLETED";

    public OperationCompletedEvent(String assemblyLineId,
                                   UUID executionId,
                                   String operationId,
                                   Object input,
                                   Object output) {
        super(assemblyLineId, executionId, TYPE, operationId, input, output);
    }
}
