package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public class OperationStartedEvent extends OperationBaseEvent {
    private static final String TYPE = "OPERATION_STARTED";

    public OperationStartedEvent(String pipelineId, UUID executionId, String operationId, Object input) {
        super(pipelineId, executionId, TYPE, operationId, input);
    }
}
