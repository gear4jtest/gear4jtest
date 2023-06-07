package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.Operation;

public class ItemExecution {

	private AssemblyLineExecution assemblyLineExecution;

	private List<LineElementExecution> lineElementExecutions;

	private Map<String, Object> context;

	ItemExecution(AssemblyLineExecution assemblyLineExecution, Map<String, Object> context) {
		this.assemblyLineExecution = assemblyLineExecution;
		this.context = new HashMap<>();
		this.lineElementExecutions = new ArrayList<>();
	}

	public StepExecution createStepProcessorChainExecution(Operation<?, ?> operation) {
		StepExecution lineElementExecution = new StepExecution(this, operation);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public BranchExecution createBranchExecution() {
		BranchExecution lineElementExecution = new BranchExecution(this);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public BranchesExecution createBranchesExecution() {
		BranchesExecution lineElementExecution = new BranchesExecution(this);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public LineElementExecution createLineElementExecution(LineElement lineElement) {
		LineElementExecution lineElementExecution = lineElement.createLineElementExecution(this);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
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

	public EventTriggerService getEventTriggerService() {
		return assemblyLineExecution.getEventTriggerService();
	}

}
