package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.internal.ContainerLineElement;

public class ContainerExecution extends ExecutionContainer<ContainerLineElement> {

	public ContainerExecution(ContainerLineElement element, ExecutionContainer<?> parentLineOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(element, parentLineOperatorExecution, assemblyLineExecution);
	}

}
