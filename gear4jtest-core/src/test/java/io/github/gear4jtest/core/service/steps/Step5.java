package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;

public class Step5 implements Operation<Void, String> {

	@Override
	public String execute(Void object, ExecutionContext context) {
		return "b";
	}

}
