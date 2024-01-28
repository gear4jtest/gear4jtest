package io.github.gear4jtest.core.context;

import io.github.gear4jtest.core.internal.LineOperator;

public class LineOperatorExecution extends ExecutionContainer<LineOperator> {

	public LineOperatorExecution(LineOperator element, ExecutionContainer<?> parentLineOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(element, parentLineOperatorExecution, assemblyLineExecution);
	}

	public LineOperatorExecution(LineOperator element, AssemblyLineExecution assemblyLineExecution) {
		this(element, null, assemblyLineExecution);
	}

}
