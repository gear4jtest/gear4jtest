package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.processor.ProcessorChain;

public class LineVisitor {
	
	LineVisitor() {
		
	}
	
	private Object visit(BranchesLineElement element, Object input, Gear4jContext context) {
		return input;
	}
	
	private Object visit(BranchLineElement element, Object input, Gear4jContext context) {
		return input;
	}
	
	private Object visit(StepLineElement element, Object input, Gear4jContext context) {
		ProcessorChain<StepLineElement> chain = new ProcessorChain<StepLineElement>(element.getProcessorChain(), input, context);
		return chain.processChain();
	}
	
	public Object visit(Object element, Object input, Gear4jContext context) {
		if (element instanceof BranchesLineElement) {
			return visit((BranchesLineElement) element, input, context);
		} else if (element instanceof BranchLineElement) {
			return visit((BranchLineElement) element, input, context);
		} else if (element instanceof StepLineElement) {
			return visit((StepLineElement) element, input, context);
		}
		return null;
	}
	
}
