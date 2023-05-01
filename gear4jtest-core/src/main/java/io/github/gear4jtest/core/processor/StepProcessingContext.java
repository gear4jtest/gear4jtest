package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;

public class StepProcessingContext extends BaseProcessingContext<StepLineElement> {

	private Operation operation;

	public StepProcessingContext() {
	}
	
	public StepProcessingContext(Operation operation) {
		this.operation = operation;
	}

	public Operation getOperation() {
		return operation;
	}
	
	public void setOperation(Operation operation) {
		this.operation = operation;
	}

}
