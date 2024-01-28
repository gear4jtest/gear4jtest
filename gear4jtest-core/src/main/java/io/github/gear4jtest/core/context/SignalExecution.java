package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.internal.SignalLineElement;

public class SignalExecution extends AssemblyLineOperatorExecution {

	public SignalExecution(SignalLineElement element, ExecutionContainer<?> parentOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(element, parentOperatorExecution, assemblyLineExecution);
	}

}
