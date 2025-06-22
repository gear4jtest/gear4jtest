package io.github.gear4jtest.core.event;

public class OperationErrorEvent extends OperationBaseEvent {
	private static final String TYPE = "OPERATION_ERROR";

	private final Exception exception;

	public OperationErrorEvent(String pipelineId, String executionId, String operationId, Object input, Exception exception) {
        super(pipelineId, executionId, operationId, TYPE, input);
		this.exception = exception;
	}

}
