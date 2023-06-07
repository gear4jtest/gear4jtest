package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.context.ItemExecution;

public class AssemblyLineOrchestrator {

	private final LineTraverser lineTraverser;

	private final AssemblyLineExecution execution;

	AssemblyLineOrchestrator(AssemblyLineExecution execution) {
		this.lineTraverser = new LineTraverser();
		this.execution = execution;
	}

	// Make this class only works with Item => this method should get an Item as parameter, not a simple Object
	public <BEGIN, OUT> Item command(AssemblyLine<BEGIN, OUT> line, Item input) {
		return command(line.getStartingElement(), input);
	}

	private <BEGIN, OUT> Item command(LineElement element, Item input) {
//		Item result = lineVisitor.visit(element, input);
		Item result = element.execute(input);

		List<LineElement> nextElements = lineTraverser.getNextElement(element);
		if (nextElements.isEmpty()) {
			return result;
		}

		List<Item> returns = new ArrayList<>(nextElements.size());
		for (LineElement child : nextElements) {
			Item ret = command(child, result);
			returns.add(ret);
		}
		return aggregateResults(returns);
	}

	private Item aggregateResults(List<Item> returns) {
		if (returns.size() == 1) {
			return returns.get(0);
		} else {
			return new Item(returns.stream().map(Item::getItem).collect(Collectors.toList()),
					execution.createItemExecution(returns.stream().map(Item::getExecution)
							.map(ItemExecution::getContext).flatMap(map -> map.entrySet().stream())
							.collect(Collectors.toMap(Entry::getKey, Entry::getValue))));
		}
	}

//	public List<Object> executeElements(List<LineElement> elements) {
//		List<Object> results = new ArrayList<>();
//		for (LineElement element : elements) {
//			results.add(executeElement(element));
//		}
//		return results;
//	}

}
