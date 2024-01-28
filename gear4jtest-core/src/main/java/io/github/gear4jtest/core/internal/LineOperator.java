package io.github.gear4jtest.core.internal;

import java.util.function.BiPredicate;

import io.github.gear4jtest.core.context.AssemblyLineOperatorExecution;
import io.github.gear4jtest.core.context.LineOperatorExecution;
import io.github.gear4jtest.core.model.refactor.LineDefinition;

public class LineOperator extends AssemblyLineOperator<LineOperatorExecution> {

	private final AssemblyLineOperator<?> firstElement;
	private final BiPredicate<Object, LineOperatorExecution> condition;

	public LineOperator(AssemblyLineOperator<?> firstElement, LineDefinition<?, ?> lineDefinition) {
		super();
		this.firstElement = firstElement;
		this.condition = (BiPredicate<Object, LineOperatorExecution>) lineDefinition.getCondition();
	}

	@Override
	public LineOperatorExecution execute(LineOperatorExecution execution) {
		if (!isApplicable(execution)) {
			execution.getItem().updateItem(null);
			return execution;
		}

		AssemblyLineOperatorExecution resultExecution = new AssemblyLineOrchestrator(execution.getAssemblyLineExecution()).orchestrate(firstElement, execution);
		execution.getItem().updateItem(resultExecution.getItem().getItem());
		return execution;
	}

	private boolean isApplicable(LineOperatorExecution execution) {
		if (condition == null) {
			return true;
		}
		return this.condition.test(execution.getItem().getItem(), execution);
	}
}
