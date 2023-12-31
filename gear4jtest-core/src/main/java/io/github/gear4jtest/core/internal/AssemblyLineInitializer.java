package io.github.gear4jtest.core.internal;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.event.EventQueueInitializer;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;

public class AssemblyLineInitializer {

	private final List<Initializer> defaultInitializers;
	private final AssemblyLineDefinition assemblyLineDefinition;
	private final AssemblyLineExecution execution;

	AssemblyLineInitializer(AssemblyLineDefinition assemblyLineDefinition, AssemblyLineExecution execution) {
		this.assemblyLineDefinition = assemblyLineDefinition;
		this.execution = execution;
		this.defaultInitializers = Arrays.asList(new EventQueueInitializer());
	}

	public void initialize() {
		for (Initializer defaultInitializer : defaultInitializers) {
			defaultInitializer.initialize(assemblyLineDefinition, execution);
		}
	}

}
