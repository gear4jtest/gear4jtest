package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.model.Operation;

public class StepExecution extends LineElementExecution {

	private Operation operation;

	public StepExecution(ItemExecution itemExecution, Operation operation) {
		super(itemExecution);
		this.operation = operation;
	}

	public Operation getOperation() {
		return operation;
	}

}
