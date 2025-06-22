package io.github.gear4jtest.core.event;

public class OperationStartedEvent extends OperationBaseEvent {
	private static final String TYPE = "OPERATION_STARTED";

	public OperationStartedEvent(String pipelineId, String executionId, String operationId, Object input) {
        super(pipelineId, executionId, operationId, TYPE, input);
	}

}
