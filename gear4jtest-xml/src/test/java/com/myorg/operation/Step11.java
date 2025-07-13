package com.myorg.operation;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector.Parameter;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step11 implements Transformer<String, String> {

	private Parameter<String> param = Parameter.of();

	@Override
	public String transform(String object, ExecutionContext context, OperationExecution operationExecution) {
		return param.getValue();
	}

	public Parameter<String> getParam() {
		return param;
	}

}
