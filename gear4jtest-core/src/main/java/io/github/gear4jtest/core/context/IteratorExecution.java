package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.internal.IteratorLineElement;

public class IteratorExecution extends ExecutionContainer<IteratorLineElement> {

	public IteratorExecution(IteratorLineElement element, ExecutionContainer<?> parentOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		super(element, parentOperatorExecution, assemblyLineExecution);
	}

}
