package io.github.gear4jtest.core.internal;

import java.util.List;

import io.github.gear4jtest.core.context.AssemblyLineOperatorExecution;
import io.github.gear4jtest.core.context.ContainerExecution;
import io.github.gear4jtest.core.context.LineOperatorExecution;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;

public class ContainerLineElement extends AssemblyLineOperator<ContainerExecution> {

	private final ContainerDefinition<?, ?> containerDefinition;
	private final List<LineOperator> childrenElements;

	public ContainerLineElement(ContainerDefinition<?, ?> containerDefinition, List<LineOperator> childrenElements) {
		super();
		this.childrenElements = childrenElements;
		this.containerDefinition = containerDefinition;
	}

	@Override
	public ContainerExecution execute(ContainerExecution execution) {
		Object input = execution.getItem().getItem();
		for (LineOperator element : childrenElements) {
			Object newObject = deepClone(input);
//			LineOperatorExecution newItemExecution = execution.getAssemblyLineExecution().createExecution(element, execution);
//			newItemExecution.getItem().updateItem(newObject);
			new AssemblyLineOrchestrator(execution.getAssemblyLineExecution()).orchestrate(element, execution, new Item(newObject));
		}
		execution.getItem().updateItem(returns(execution.getExecutions()));
		return execution;
	}

	private Object returns(List<AssemblyLineOperatorExecution> executions) {
		Object[] returnedObjects = executions.stream()
				.map(AssemblyLineOperatorExecution::getItem)
				.map(Item::getItem)
				.toArray();
		if (containerDefinition.getFunction() != null) {
			return containerDefinition.getFunction().apply(returnedObjects);
		} else {
			return null;
		}
	}
	
	private Object deepClone(Object object) {
		return object;
	}
}
