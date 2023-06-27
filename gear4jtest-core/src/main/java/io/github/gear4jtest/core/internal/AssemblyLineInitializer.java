package io.github.gear4jtest.core.internal;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.event.EventQueueInitializer;
import io.github.gear4jtest.core.model.ChainModel;

public class AssemblyLineInitializer {

	private final List<Initializer> defaultInitializers;
	private final ChainModel model;
	private final AssemblyLineExecution execution;

	AssemblyLineInitializer(ChainModel model, AssemblyLineExecution execution) {
		this.model = model;
		this.execution = execution;
		this.defaultInitializers = Arrays.asList(new EventQueueInitializer());
	}

	public void initialize() {
		for (Initializer defaultInitializer : defaultInitializers) {
			defaultInitializer.initialize(model, execution);
		}
	}

}
