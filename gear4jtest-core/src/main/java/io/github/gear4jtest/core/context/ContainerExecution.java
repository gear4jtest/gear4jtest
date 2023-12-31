package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.ContainerLineElement;

public class ContainerExecution extends LineElementExecution {

	private final UUID id;
	private final List<ItemExecution> executions;
	private final ItemExecution parentItemExecution;

	public ContainerExecution(ItemExecution itemExecution, ContainerLineElement element) {
		super(itemExecution);
		this.id = element.getId();
		this.executions = new ArrayList<>();
		this.parentItemExecution = itemExecution;
	}

	public ItemExecution createItemExecution(Object input) {
		return createItemExecution(input, new HashMap<>());
	}

	public ItemExecution createItemExecution(Object input, Map<String, Object> context) {
		return new ItemExecution(parentItemExecution.getAssemblyLineExecution(), input, context);
	}

	public List<ItemExecution> getExecutions() {
		return executions;
	}

	public void registerExecution(ItemExecution execution) {
		executions.add(execution);
	}

	public UUID getId() {
		return id;
	}

	public Map<String, Object> getChainContext() {
		return getItemExecution().getAssemblyLineExecution().getContext();
	}

}
