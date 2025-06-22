package io.github.gear4jtest.core.event;

public class OperationBaseEvent extends Event {

	private String operationId;
	private Object input;
	private Object output;

	public OperationBaseEvent(String pipelineId, String executionId, String type, String operationId, Object input, Object output) {
        super(pipelineId, executionId, type);
		this.operationId = operationId;
		this.input = input;
		this.output = output;
	}

	public OperationBaseEvent(String pipelineId, String executionId, String type, String operationId, Object input) {
		super(pipelineId, executionId, type);
		this.operationId = operationId;
		this.input = input;
	}
}
