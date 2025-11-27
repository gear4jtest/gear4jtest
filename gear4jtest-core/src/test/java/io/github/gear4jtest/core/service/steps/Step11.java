package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step11 implements Transformer<String, String> {

	private OperationParamsInjector.Parameter<String> param = OperationParamsInjector.Parameter.<String>newBuilder().build();

	@Override
	public String transform(String object, ExecutionContext context, OperationExecutionContext operationExecution) {
		return param.getValue();
	}

	public OperationParamsInjector.Parameter<String> getParam() {
		return param;
	}

}
