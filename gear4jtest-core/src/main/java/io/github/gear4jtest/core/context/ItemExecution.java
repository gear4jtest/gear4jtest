package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;

public class ItemExecution {

	private Item item;

	private AssemblyLineExecution assemblyLineExecution;

	private List<LineElementExecution> lineElementExecutions;

	private Map<String, Object> context;

	ItemExecution(AssemblyLineExecution assemblyLineExecution, Object input, Map<String, Object> context) {
		this.assemblyLineExecution = assemblyLineExecution;
		this.item = new Item(input);
		this.context = new HashMap<>();
		this.lineElementExecutions = new ArrayList<>();
	}

	public StepExecution createStepProcessorChainExecution(StepLineElement element, Operation<?, ?> operation) {
		StepExecution lineElementExecution = new StepExecution(this, element, operation);
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

//	public LineElementExecution createLineElementExecution(LineElement lineElement) {
//		LineElementExecution lineElementExecution = lineElement.createLineElementExecution(this);
//		lineElementExecutions.add(lineElementExecution);
//		return lineElementExecution;
//	}

	@Override
	public ItemExecution clone() {
		return new ItemExecution(assemblyLineExecution, item, new HashMap<>(context));
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

	public Item getItem() {
		return item;
	}
	
	public void updateItem(Object newItem) {
		item = item.withItem(newItem);
	}

}
