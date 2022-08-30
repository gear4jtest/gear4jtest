package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.BranchLineElement;
import io.github.gear4jtest.core.model.BranchesLineElement;
import io.github.gear4jtest.core.model.Step;
import io.github.gear4jtest.core.model.StepLineElement;

public class LineVisitor {
	
	private Object visit(BranchesLineElement element, Object input) {
		return input;
	}
	
	private Object visit(BranchLineElement element, Object input) {
		return input;
	}
	
	private Object visit(StepLineElement element, Object input) {
		return ((Step) element.getStep().getOperation().get()).execute(input);
	}
	
	public Object visit(Object element, Object input) {
		if (element instanceof BranchesLineElement) {
			return visit((BranchesLineElement) element, input);
		} else if (element instanceof BranchLineElement) {
			return visit((BranchLineElement) element, input);
		} else if (element instanceof StepLineElement) {
			return visit((StepLineElement) element, input);
		}
		return null;
	}
	
}
