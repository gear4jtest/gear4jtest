package io.github.gear4jtest.core.internal;

import java.util.List;

import io.github.gear4jtest.core.context.ContainerExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;

public class ContainerLineElement extends LineElement {

	private final ContainerDefinition containerDefinition;
	private final List<LineElement> childrenElements;

	public ContainerLineElement(ContainerDefinition containerDefinition, List<LineElement> childrenElements) {
		super();
		this.containerDefinition = containerDefinition;
		this.childrenElements = childrenElements;
	}

	@Override
	public LineElementExecution execute(ItemExecution itemExecution) {
		ContainerExecution containerProcessorChainExecution = itemExecution.createContainerExecution(this);
		Object input = itemExecution.getItem().getItem();
		for (LineElement element : childrenElements) {
			Object newObject = deepClone(input);
			ItemExecution newItemExecution = containerProcessorChainExecution.createItemExecution(newObject);
			ItemExecution resultExecution = new AssemblyLineOrchestrator(itemExecution.getAssemblyLineExecution())
					.orchestrate(element, newItemExecution);
			containerProcessorChainExecution.registerExecution(resultExecution);
		}
		itemExecution.updateItem(returns(containerProcessorChainExecution.getExecutions()));
		return containerProcessorChainExecution;
	}

	private Object returns(List<ItemExecution> executions) {
		if (containerDefinition.getFunc() != null) {
			return containerDefinition.getFunc().apply(executions.get(0).getItem().getItem());
		} else if (containerDefinition.getBiFunc() != null) {
			return containerDefinition.getBiFunc().apply(executions.get(0).getItem().getItem(), executions.get(1).getItem().getItem());
		} else {
			return null;
		}
	}
	
	private Object deepClone(Object object) {
		return object;
	}
}
