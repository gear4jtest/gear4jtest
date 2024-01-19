package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder.LineElementExecutionData;

public class AssemblyLineOrchestrator {

	private final AssemblyLineExecution execution;

	AssemblyLineOrchestrator(AssemblyLineExecution execution) {
		this.execution = execution;
	}

	public <BEGIN, OUT> AssemblyLineExecution orchestrate(AssemblyLine<BEGIN, OUT> line, Object input) {
		orchestrate(line.getStartingElement(), execution.createItemExecution(input));
		return execution;
	}

	public <BEGIN, OUT> ItemExecution orchestrate(LineElement element, ItemExecution itemExecution) {
		if (itemExecution.shouldStop()) {
			return itemExecution;
		}
		LineElementExecution execution = itemExecution.createExecution(element);
		LineElementExecution result = element.execute(execution);
		itemExecution.updateItem(result.getResult());
		itemExecution.getEventTriggerService().publishEvent(new LineElementEventBuilder().buildEvent(element.getId(), new LineElementExecutionData(result)));
		
		List<LineElement> nextElements = element.getNextLineElements();
		if (nextElements.isEmpty()) {
			return itemExecution;
		}

		List<ItemExecution> returns = new ArrayList<>(nextElements.size());
		for (LineElement child : nextElements) {
			ItemExecution ret = orchestrate(child, itemExecution);
			returns.add(ret);
		}
		return aggregateResults(returns);
	}

	private ItemExecution aggregateResults(List<ItemExecution> returns) {
		if (returns.size() == 1) {
			return returns.get(0);
		} else {
			return execution.createItemExecution(returns.stream().map(ItemExecution::getItem).collect(Collectors.toList()));
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
