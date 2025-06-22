package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector;

public class Step11 implements Operation<String, String> {

	private OperationParamsInjector.Parameter<String> param = OperationParamsInjector.Parameter.of();

	@Override
	public String execute(String object, ExecutionContext context) {
		return param.getValue();
	}

	public OperationParamsInjector.Parameter<String> getParam() {
		return param;
	}

}
