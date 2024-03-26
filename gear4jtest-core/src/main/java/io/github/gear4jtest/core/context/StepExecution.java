package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;

public class StepExecution extends AssemblyLineOperatorExecution {

	private Operation<?, ?> operation;

	public StepExecution(StepLineElement lineElement, ExecutionContainer<?> parentOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(lineElement, parentOperatorExecution, assemblyLineExecution);
	}

	public <T extends Operation<?, ?>> T getOperation() {
		return (T) operation;
	}

	public void withOperation(Operation<?, ?> operation) {
		this.operation = operation;
	}

}
