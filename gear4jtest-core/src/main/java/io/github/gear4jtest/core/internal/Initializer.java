package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.model.ChainModel;

@FunctionalInterface
public interface Initializer {

	void initialize(ChainModel model, AssemblyLineExecution execution);
	
}
