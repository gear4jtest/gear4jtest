package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.context.AssemblyLineOperatorExecution;
import io.github.gear4jtest.core.context.ExecutionContainer;
import io.github.gear4jtest.core.context.LineOperatorExecution;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder.LineElementExecutionData;

public class AssemblyLineOrchestrator {

	private final AssemblyLineExecution assemblyLineExecution;

	AssemblyLineOrchestrator(AssemblyLineExecution execution) {
		this.assemblyLineExecution = execution;
	}

	public <BEGIN, OUT> AssemblyLineExecution orchestrate(AssemblyLine<BEGIN, OUT> line, Object input) {
		LineOperatorExecution rootLineExecution = assemblyLineExecution.createLineExecution(line.getStartingElement(), input);
		orchestrate(line.getStartingElement(), rootLineExecution, rootLineExecution);
		return assemblyLineExecution;
	}

	public <A extends AssemblyLineOperatorExecution> ExecutionContainer<?> orchestrate(AssemblyLineOperator<A> element, A execution, ExecutionContainer<?> lineExecution) {
		A result = element.execute(execution);
		lineExecution.getItem().updateItem(result.getItem().getItem());
		lineExecution.getEventTriggerService().publishEvent(new LineElementEventBuilder().buildEvent(element.getId(), new LineElementExecutionData(result)));
		
		List<AssemblyLineOperator> nextElements = element.getNextLineElements();
		if (nextElements.isEmpty()) {
			return lineExecution;
		}

		List<AssemblyLineOperatorExecution> returns = new ArrayList<>(nextElements.size());
		for (AssemblyLineOperator child : nextElements) {
			AssemblyLineOperatorExecution ret = orchestrate(child, lineExecution);
			returns.add(ret);
		}
		return aggregateResults(returns, lineExecution);
	}

	public <A extends AssemblyLineOperatorExecution> ExecutionContainer<?> orchestrate(AssemblyLineOperator<A> element, ExecutionContainer<?> lineExecution) {
		if (lineExecution.shouldStop()) {
			return lineExecution;
		}
		A execution = assemblyLineExecution.createExecution(element, lineExecution);
		return orchestrate(element, execution, lineExecution);
	}

	public <A extends AssemblyLineOperatorExecution> ExecutionContainer<?> orchestrate(AssemblyLineOperator<A> element, ExecutionContainer<?> lineExecution, Item newInput) {
		if (lineExecution.shouldStop()) {
			return lineExecution;
		}
		A execution = assemblyLineExecution.createExecution(element, lineExecution);
		execution.getItem().updateItem(newInput.getItem());
		return orchestrate(element, execution, lineExecution);
	}

	private ExecutionContainer<?> aggregateResults(List<AssemblyLineOperatorExecution> returns, ExecutionContainer<?> lineExecution) {
		if (returns.size() == 1) {
			lineExecution.getItem().updateItem(returns.get(0).getItem().getItem());
		} else {
			lineExecution.getItem().updateItem(returns.stream().map(AssemblyLineOperatorExecution::getItem).map(Item::getItem).collect(Collectors.toList()));
		}
		return lineExecution;
	}

//	public List<Object> executeElements(List<LineElement> elements) {
//		List<Object> results = new ArrayList<>();
//		for (LineElement element : elements) {
//			results.add(executeElement(element));
//		}
//		return results;
//	}

}
