package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
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
	public <BEGIN, OUT> Item command(AssemblyLine<BEGIN, OUT> line, Object input) {
		return command(line.getStartingElement(), new Item(input), execution.createItemExecution());
	}

	private <BEGIN, OUT> Item command(LineElement element, Item input, ItemExecution itemExecution) {
//		Item result = lineVisitor.visit(element, input);
		Item result = element.execute(input, itemExecution);

		List<LineElement> nextElements = lineTraverser.getNextElement(element);
		if (nextElements.isEmpty()) {
			return result;
		}

		List<Item> returns = new ArrayList<>(nextElements.size());
		for (LineElement child : nextElements) {
			Item ret = command(child, result, itemExecution);
			returns.add(ret);
		}
		return aggregateResults(returns);
	}

	private Item aggregateResults(List<Item> returns) {
		if (returns.size() == 1) {
			return returns.get(0);
		} else {
			return new Item(returns.stream().map(Item::getItem).collect(Collectors.toList()));
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
