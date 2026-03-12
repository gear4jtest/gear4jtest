package io.github.gear4jtest.core.event;

import java.util.UUID;

public class OperationBaseEvent extends Event {

	private String operationId;
	private Object input;
	private Object output;

	public OperationBaseEvent(String pipelineId, UUID executionId, String type, String operationId, Object input, Object output) {
        super(pipelineId, executionId, type);
		this.operationId = operationId;
		this.input = input;
		this.output = output;
	}

	public OperationBaseEvent(String pipelineId, UUID executionId, String type, String operationId, Object input) {
		super(pipelineId, executionId, type);
		this.operationId = operationId;
		this.input = input;
	}

	public String getOperationId() {
		return operationId;
	}
	public Object getOutput() {
		return output;
	}
}
