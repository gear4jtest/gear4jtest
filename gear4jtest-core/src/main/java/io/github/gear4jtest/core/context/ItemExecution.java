package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.Operation;

public class ItemExecution {

	private AssemblyLineExecution assemblyLineExecution;

	private Map<String, Object> context;

	public ItemExecution(AssemblyLineExecution assemblyLineExecution) {
		this(assemblyLineExecution, new HashMap<>());
	}

	public ItemExecution(AssemblyLineExecution assemblyLineExecution, Map<String, Object> context) {
		this.assemblyLineExecution = assemblyLineExecution;
		this.context = new HashMap<>();
	}
	
	public StepProcessorChainExecution createStepProcessorChainExecution(Operation<?, ?> operation) {
		return new StepProcessorChainExecution(this, operation);
	}
	
	@Override
	public ItemExecution clone() {
		return new ItemExecution(assemblyLineExecution, new HashMap<>(context));
	}

	public AssemblyLineExecution getAssemblyLineExecution() {
		return assemblyLineExecution;
	}

	public void setAssemblyLineExecution(AssemblyLineExecution assemblyLineExecution) {
		this.assemblyLineExecution = assemblyLineExecution;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

}
