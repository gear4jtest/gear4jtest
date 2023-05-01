package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.context.Gear4jContext;

public class LineCommander {

	private final LineTraverser lineTraverser;
	private final LineVisitor lineVisitor;
	
	LineCommander() {
		this.lineTraverser = new LineTraverser();
		this.lineVisitor = new LineVisitor();
	}

	public <BEGIN, OUT> Object command(AssemblyLine<BEGIN, OUT> line, Object input, Gear4jContext context) {
		return command(line.getStartingElement(), input, context);
	}

	private <BEGIN, OUT> Object command(LineElement element, Object input, Gear4jContext context) {
		Object result = executeElement(element, input, context);

		List<LineElement> nextElements = lineTraverser.getNextElement(element);
		List<Object> returns = new ArrayList<>(nextElements.size());
		for (LineElement child : nextElements) {
			Object ret = command(child, result, context);
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

	public Object executeElement(LineElement element, Object input, Gear4jContext context) {
		return lineVisitor.visit(element, input, context);
	}

}
