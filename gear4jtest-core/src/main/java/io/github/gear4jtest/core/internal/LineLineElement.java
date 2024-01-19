package io.github.gear4jtest.core.internal;

import java.util.function.BiPredicate;

import io.github.gear4jtest.core.context.ContainerExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.LineExecution;

public class LineLineElement extends LineElement<LineExecution> {

	private final LineElement firstElement;
	private final BiPredicate<Object, ItemExecution> condition;

	public LineLineElement(LineElement firstElement, BiPredicate<Object, ItemExecution> condition) {
		super();
		this.firstElement = firstElement;
		this.condition = condition;
	}

	@Override
	public LineElementExecution execute(LineExecution execution) {
		if (!isApplicable(execution.getItemExecution())) {
			execution.setResult(null);
			return execution;
		}

//			ItemExecution newItemExecution = execution.createItemExecution(newObject);
//			ItemExecution resultExecution = new AssemblyLineOrchestrator(execution.getItemExecution().getAssemblyLineExecution())
//					.orchestrate(element, newItemExecution);
//			execution.registerExecution(resultExecution);
////		execution.getItemExecution().updateItem(returns(execution.getExecutions()));
//		execution.setResult(returns(execution.getExecutions()));
		return execution;
	}

	private boolean isApplicable(ItemExecution execution) {
		return this.condition.test(execution.getItem().getItem(), execution);
	}
}
