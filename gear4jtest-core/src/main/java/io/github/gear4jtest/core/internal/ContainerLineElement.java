package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.AssemblyLineOperatorExecution;
import io.github.gear4jtest.core.context.ContainerExecution;
import io.github.gear4jtest.core.model.refactor.ContainerBaseDefinition;

import java.util.List;

public class ContainerLineElement extends AssemblyLineOperator<ContainerExecution> {

	private final ContainerBaseDefinition<?, ?> containerDefinition;
	private final List<LineOperator> childrenElements;

	public ContainerLineElement(ContainerBaseDefinition<?, ?> containerDefinition, List<LineOperator> childrenElements) {
		super();
		this.childrenElements = childrenElements;
		this.containerDefinition = containerDefinition;
	}

	@Override
	public ContainerExecution execute(ContainerExecution execution) {
		Object input = execution.getItem().getItem();
		AssemblyLineOrchestrator orchestrator = new AssemblyLineOrchestrator(execution.getAssemblyLineExecution());
		for (LineOperator element : childrenElements) {
			Object newObject = deepClone(input);
//			LineOperatorExecution newItemExecution = execution.getAssemblyLineExecution().createExecution(element, execution);
//			newItemExecution.getItem().updateItem(newObject);
			orchestrator.orchestrate(element, execution);
		}
		execution.getItem().updateItem(returns(execution.getExecutions()));
		return execution;
	}

	private Object returns(List<AssemblyLineOperatorExecution> executions) {
		Object[] returnedObjects = executions.stream()
				.map(AssemblyLineOperatorExecution::getItem)
				.map(Item::getItem)
				.toArray();
		if (containerDefinition.getFunc() != null) {
			return containerDefinition.getFunc().apply(returnedObjects);
		} else {
			return null;
		}
	}

	private Object deepClone(Object object) {
		return object;
	}
}
