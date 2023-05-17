package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.Operation;

public class StepProcessorChainExecution {

	private ItemExecution itemExecution;

	private Operation operation;

	private Map<String, Object> context;

	public StepProcessorChainExecution(ItemExecution itemExecution, Operation operation) {
		this.itemExecution = itemExecution;
		this.context = new HashMap<>();
		this.operation = operation;
	}

	public ItemExecution getItemExecution() {
		return itemExecution;
	}

	public void setItemExecution(ItemExecution itemExecution) {
		this.itemExecution = itemExecution;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public Operation getOperation() {
		return operation;
	}

	public void setOperation(Operation operation) {
		this.operation = operation;
	}

}
