package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.model.Operation;

public class StepProcessingContext extends LineElementContext {

	private Operation operation;
	
	public StepProcessingContext(Operation operation) {
		this.operation = operation;
	}

	public Operation getOperation() {
		return operation;
	}

}
