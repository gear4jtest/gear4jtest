package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collector;

import io.github.gear4jtest.core.context.IteratorExecution;
import io.github.gear4jtest.core.context.LineOperatorExecution;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.Accumulator;

public class IteratorLineElement extends AssemblyLineOperator<IteratorExecution> {

	private final Function<Object, Iterable<?>> func;
	private final Accumulator accumulator;
	private final Collector collector;
	private final LineOperator nestedElement;

	public IteratorLineElement(IteratorDefinition<?, ?> iterator, LineOperator lineElement) {
		super();
		this.func = (Function<Object, Iterable<?>>) iterator.getFunc();
		this.accumulator = iterator.getAccumulator();
		this.collector = iterator.getCollector();
		this.nestedElement = lineElement;
	}

	@Override
	public IteratorExecution execute(IteratorExecution execution) {
		Iterable<?> iterable = func.apply(execution.getItem().getItem());
		
		Collection<Object> collection = new ArrayList<>();
		
		for (Object element : iterable) {
			LineOperatorExecution newItemExecution = execution.getAssemblyLineExecution().createExecution(nestedElement, execution.getParentOperatorExecution());
			newItemExecution.getItem().updateItem(element);
			LineOperatorExecution resultExecution = (LineOperatorExecution) new AssemblyLineOrchestrator(execution.getAssemblyLineExecution()).orchestrate(nestedElement, newItemExecution, new Item(element));
			accumulateIfNecessary(resultExecution, collection);
		}
		
		Object finalItem = collector == null ? null : collection.stream().collect(collector);
		execution.getItem().updateItem(finalItem);
		return execution;
	}

	private void accumulateIfNecessary(LineOperatorExecution execution, Collection<Object> collection) {
		if (collection == null) {
			return;
		}
		collection.add(execution.getItem().getItem());
	}

	public Function<?, ?> getFunc() {
		return func;
	}

}
