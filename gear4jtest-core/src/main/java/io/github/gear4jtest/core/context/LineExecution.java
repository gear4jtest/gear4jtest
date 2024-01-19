package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.LineLineElement;

public class LineExecution extends LineElementExecution {

	private final UUID id;
	private final List<LineElementExecution> executions;
	private final ItemExecution parentItemExecution;

	public LineExecution(ItemExecution itemExecution, LineLineElement element) {
		super(itemExecution);
		this.id = element.getId();
		this.executions = new ArrayList<>();
		this.parentItemExecution = itemExecution;
	}

	public List<LineElementExecution> getExecutions() {
		return executions;
	}

	public void registerExecution(LineElementExecution execution) {
		executions.add(execution);
	}

	public UUID getId() {
		return id;
	}

	public Map<String, Object> getChainContext() {
		return getItemExecution().getAssemblyLineExecution().getContext();
	}

}
