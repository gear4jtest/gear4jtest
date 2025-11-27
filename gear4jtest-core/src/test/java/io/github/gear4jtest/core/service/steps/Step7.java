package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;

public class Step7 extends Step6 {

	@Override
	public String transform(String object, ExecutionContext context, OperationExecutionContext operationExecution) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		return object/*.getValue()*/.toString() + "_" + context.get("a", Object.class);
	}

}
