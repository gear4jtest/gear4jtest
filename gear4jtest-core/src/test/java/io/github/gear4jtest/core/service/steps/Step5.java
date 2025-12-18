package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step5 implements Operator<Void, String> {

	@Override
	public String transform(Void object, ExecutionContext context, StationExecutionContext operationExecution) {
		return "b";
	}

}
