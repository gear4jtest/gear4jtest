package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collector;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.IteratorExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.Accumulator;

public class IteratorLineElement extends LineElement {

	private final Function<Object, Iterable<?>> func;
	private final Accumulator accumulator;
	private final Collector collector;
	private final LineElement nestedElement;

	public IteratorLineElement(IteratorDefinition<?> iterator, LineElement lineElement) {
		super();
		this.func = (Function<Object, Iterable<?>>) iterator.getFunc();
		this.accumulator = iterator.getAccumulator();
		this.collector = iterator.getCollector();
		this.nestedElement = lineElement;
	}

	@Override
	public LineElementExecution execute(ItemExecution itemExecution) {
		IteratorExecution iteratorProcessorChainExecution = itemExecution.createIteratorExecution(this);
		Iterable<?> iterable = func.apply(itemExecution.getItem().getItem());
		
		Collection<Object> collection = new ArrayList<>();
		
		for (Object element : iterable) {
			ItemExecution newItemExecution = iteratorProcessorChainExecution.createItemExecution(element);
			ItemExecution resultExecution = new AssemblyLineOrchestrator(itemExecution.getAssemblyLineExecution())
					.orchestrate(nestedElement, newItemExecution);
			iteratorProcessorChainExecution.registerExecution(resultExecution);
			
			accumulateIfNecessary(resultExecution, collection);
		}
		
		Object finalItem = collector == null ? null : collection.stream().collect(collector);
		itemExecution.updateItem(collector == null ? null : finalItem);
		return iteratorProcessorChainExecution;
	}

	private void accumulateIfNecessary(ItemExecution execution, Collection<Object> collection) {
		if (collection == null) {
			return;
		}
		collection.add(execution.getItem().getItem());
	}

	public Function<?, ?> getFunc() {
		return func;
	}

	public LineElement getElement() {
		return nestedElement;
	}

}
