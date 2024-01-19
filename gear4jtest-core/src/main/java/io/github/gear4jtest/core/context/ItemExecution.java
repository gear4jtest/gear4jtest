package io.github.gear4jtest.core.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.internal.ContainerLineElement;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.IteratorLineElement;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.internal.LineLineElement;
import io.github.gear4jtest.core.internal.SignalLineElement;
import io.github.gear4jtest.core.internal.StepLineElement;

public class ItemExecution {

	private Item item;

	private AssemblyLineExecution assemblyLineExecution;

	private List<LineElementExecution> lineElementExecutions;

	private Map<String, Object> context;
	
	private Iterator<?> it;
	
	private boolean shouldStop = false;

	ItemExecution(AssemblyLineExecution assemblyLineExecution, Object input, Map<String, Object> context) {
		this.assemblyLineExecution = assemblyLineExecution;
		this.item = new Item(input);
		this.context = new HashMap<>();
		this.lineElementExecutions = new ArrayList<>();
	}

	public LineElementExecution createExecution(LineElement element) {
		LineElementExecution lineElementExecution = buildExecution(element);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	private LineElementExecution buildExecution(LineElement element) {
		if (element instanceof StepLineElement) {
			return new StepExecution(this, (StepLineElement) element);
		} else if (element instanceof SignalLineElement) {
			return new SignalExecution(this, (SignalLineElement) element);
		} else if (element instanceof IteratorLineElement) {
			return new IteratorExecution(this, (IteratorLineElement) element);
		} else if (element instanceof ContainerLineElement) {
			return new ContainerExecution(this, (ContainerLineElement) element);
		} else if (element instanceof LineLineElement) {
			return new LineExecution(this, (LineLineElement) element);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public StepExecution createStepProcessorChainExecution(StepLineElement element) {
		StepExecution lineElementExecution = new StepExecution(this, element);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public SignalExecution createSignalExecution(SignalLineElement element) {
		SignalExecution lineElementExecution = new SignalExecution(this, element);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public IteratorExecution createIteratorExecution(IteratorLineElement element) {
		IteratorExecution lineElementExecution = new IteratorExecution(this, element);
		lineElementExecutions.add(lineElementExecution);
		return lineElementExecution;
	}

	public ContainerExecution createContainerExecution(ContainerLineElement element) {
		ContainerExecution lineElementExecution = new ContainerExecution(this, element);
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

	public boolean shouldStop() {
		return shouldStop;
	}

	public void shouldStop(boolean shouldStop) {
		this.shouldStop = shouldStop;
	}

}
