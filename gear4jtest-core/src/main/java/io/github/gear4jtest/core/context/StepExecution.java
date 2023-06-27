package io.github.gear4jtest.core.context;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;

public class StepExecution extends LineElementExecution {

	private final UUID id;
	private final Operation operation;

	public StepExecution(ItemExecution itemExecution, StepLineElement element, Operation operation) {
		super(itemExecution);
		this.id = element.getId();
		this.operation = operation;
	}

	public UUID getId() {
		return id;
	}

	public Operation getOperation() {
		return operation;
	}
	
	public Map<String, Object> getChainContext() {
		return getItemExecution().getAssemblyLineExecution().getContext();
	}

}
