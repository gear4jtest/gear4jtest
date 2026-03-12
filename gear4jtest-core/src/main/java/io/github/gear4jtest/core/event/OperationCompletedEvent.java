package io.github.gear4jtest.core.event;

import java.util.UUID;

public class OperationCompletedEvent extends OperationBaseEvent {
	private static final String TYPE = "OPERATION_COMPLETED";

	public OperationCompletedEvent(String pipelineId, UUID executionId, String operationId, Object input, Object output) {
        super(pipelineId, executionId, TYPE, operationId, input, output);
	}

}
