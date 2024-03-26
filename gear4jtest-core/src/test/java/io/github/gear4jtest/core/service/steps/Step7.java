package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.context.StepExecution;

public class Step7 extends Step6 {

	@Override
	public String execute(String object, StepExecution context) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		return value.getValue().toString() + "_" + context.getChainContext().get("a");
	}

}
