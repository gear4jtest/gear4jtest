package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;

public class Step7 extends Step6 {

	@Override
	public String transform(String object, ExecutionContext context, StationExecutionContext operationExecution) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		return object/*.getValue()*/.toString() + "_" + context.get("a", Object.class);
	}

}
