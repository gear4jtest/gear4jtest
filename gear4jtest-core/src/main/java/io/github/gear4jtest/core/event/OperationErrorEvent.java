package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public class OperationErrorEvent extends OperationBaseEvent {
    private static final String TYPE = "OPERATION_ERROR";
    private final Exception exception;

    public OperationErrorEvent(String pipelineId,
                               UUID executionId,
                               String operationId,
                               Object input,
                               Exception exception) {
        super(pipelineId, executionId, TYPE, operationId, input);
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }
}
