package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step5 implements Transformer<Void, String> {

	@Override
	public String transform(Void object, ExecutionContext context, OperationExecutionContext operationExecution) {
		return "b";
	}

}
