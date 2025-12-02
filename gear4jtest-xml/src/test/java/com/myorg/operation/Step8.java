package com.myorg.operation;

import java.util.Map;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step8 implements Transformer<Map<String, String>, Integer> {

	@Override
	public Integer transform(Map<String, String> object, ExecutionContext context, OperationExecutionContext operationExecution) {
		return 5;
	}

}
