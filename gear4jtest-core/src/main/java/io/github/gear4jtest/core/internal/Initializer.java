package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;

@FunctionalInterface
public interface Initializer {

	void initialize(AssemblyLineDefinition model, AssemblyLineExecution execution);
	
}
