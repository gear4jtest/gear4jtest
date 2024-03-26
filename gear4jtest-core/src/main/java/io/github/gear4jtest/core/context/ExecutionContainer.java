package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.internal.AssemblyLineOperator;

public class ExecutionContainer<T extends AssemblyLineOperator<?>> extends AssemblyLineOperatorExecution {

	protected final List<AssemblyLineOperatorExecution> executions;

	public ExecutionContainer(T element, ExecutionContainer<?> parentOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(element, parentOperatorExecution, assemblyLineExecution);
		this.executions = new ArrayList<>();
	}

	public List<AssemblyLineOperatorExecution> getExecutions() {
		return executions;
	}

	public void registerExecution(AssemblyLineOperatorExecution execution) {
		executions.add(execution);
	}

}
