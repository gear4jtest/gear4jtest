package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step13 implements Transformer<String, List<String>> {

	@Override
	public List<String> transform(String string, ExecutionContext context, OperationExecutionContext operationExecution) {
		return Arrays.asList(string);
	}

}
