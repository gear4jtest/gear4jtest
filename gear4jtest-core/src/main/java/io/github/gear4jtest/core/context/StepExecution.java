package io.github.gear4jtest.core.context;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;

public class StepExecution extends LineElementExecution {

	private final UUID id;

	private Operation<?, ?> operation;

	public StepExecution(ItemExecution itemExecution, StepLineElement element) {
		super(itemExecution);
		this.id = element.getId();
	}

	public UUID getId() {
		return id;
	}

	public <T extends Operation<?, ?>> T getOperation() {
		return (T) operation;
	}

	public Map<String, Object> getChainContext() {
		return getItemExecution().getAssemblyLineExecution().getContext();
	}

	public void withOperation(Operation<?, ?> operation) {
		this.operation = operation;
	}

}
