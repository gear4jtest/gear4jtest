package io.github.gear4jtest.core.internal;

import java.util.List;

import io.github.gear4jtest.core.context.ContainerExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.model.refactor.Container1Definition;
import io.github.gear4jtest.core.model.refactor.Container2Definition;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.FormerContainerDefinition;

public class ContainerLineElement extends LineElement<ContainerExecution> {

	private final FormerContainerDefinition formerContainerDefinition;
	private final Container1Definition container1Definition;
	private final Container2Definition container2Definition;
	private final ContainerDefinition containerDefinition;
	private final List<LineElement> childrenElements;

	public ContainerLineElement(FormerContainerDefinition containerDefinition, List<LineElement> childrenElements) {
		super();
		this.formerContainerDefinition = containerDefinition;
		this.childrenElements = childrenElements;
		this.container1Definition = null;
		this.container2Definition = null;
		this.containerDefinition = null;
	}

	public ContainerLineElement(Container1Definition containerDefinition, List<LineElement> childrenElements) {
		super();
		this.formerContainerDefinition = null;
		this.container1Definition = containerDefinition;
		this.childrenElements = childrenElements;
		this.container2Definition = null;
		this.containerDefinition = null;
	}

	public ContainerLineElement(Container2Definition containerDefinition, List<LineElement> childrenElements) {
		super();
		this.formerContainerDefinition = null;
		this.container1Definition = null;
		this.container2Definition = containerDefinition;
		this.childrenElements = childrenElements;
		this.containerDefinition = null;
	}

	public ContainerLineElement(ContainerDefinition containerDefinition, List<LineElement> childrenElements) {
		super();
		this.formerContainerDefinition = null;
		this.container1Definition = null;
		this.container2Definition = null;
		this.childrenElements = childrenElements;
		this.containerDefinition = containerDefinition;
	}

	@Override
	public LineElementExecution execute(ContainerExecution execution) {
		Object input = execution.getItemExecution().getItem().getItem();
		for (LineElement element : childrenElements) {
			Object newObject = deepClone(input);
			ItemExecution newItemExecution = execution.createItemExecution(newObject);
			ItemExecution resultExecution = new AssemblyLineOrchestrator(execution.getItemExecution().getAssemblyLineExecution())
					.orchestrate(element, newItemExecution);
			execution.registerExecution(resultExecution);
		}
//		execution.getItemExecution().updateItem(returns(execution.getExecutions()));
		execution.setResult(returns(execution.getExecutions()));
		return execution;
	}

	private Object returns(List<ItemExecution> executions) {
		Object[] returnedObjects = executions.stream()
				.map(ItemExecution::getItem)
				.map(Item::getItem)
				.toArray();
		if (containerDefinition.getFunction() != null) {
			return containerDefinition.getFunction().apply(returnedObjects);
		} /*else if (container1Definition.getBiFunc() != null) {
			return container1Definition.getBiFunc().apply(executions.get(0).getItem().getItem(), executions.get(1).getItem().getItem());
		} */else {
			return null;
		}
	}
	
	private Object deepClone(Object object) {
		return object;
	}
}
