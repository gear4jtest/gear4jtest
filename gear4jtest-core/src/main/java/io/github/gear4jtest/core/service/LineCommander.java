package io.github.gear4jtest.core.service;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.LineElement;

public class LineCommander {

	private LineTraverser lineTraverser = new LineTraverser();
	private LineVisitor lineVisitor = new LineVisitor();

	public <BEGIN, OUT> Object command(AssemblyLine<BEGIN, OUT> line, Object input) {
		return command(line.getStartingElement(), input);
	}

	public <BEGIN, OUT> Object command(LineElement element, Object input) {
		Object result = executeElement(element, input);

		List<LineElement> nextElements = lineTraverser.getNextElement(element);
		List<Object> returns = new ArrayList<>(nextElements.size());
		for (LineElement child : nextElements) {
			Object ret = command(child, result);
			returns.add(ret);
		}
		
		if (nextElements.isEmpty()) {
			return result;
		}
		return aggregateResults(returns);
	}

	private Object aggregateResults(List<Object> returns) {
		if (returns.size() == 1) {
			return returns.get(0);
		} else {
			return returns;
		}
	}
	
//	public List<Object> executeElements(List<LineElement> elements) {
//		List<Object> results = new ArrayList<>();
//		for (LineElement element : elements) {
//			results.add(executeElement(element));
//		}
//		return results;
//	}

	public Object executeElement(LineElement element, Object input) {
		return lineVisitor.visit(element, input);
	}

}
