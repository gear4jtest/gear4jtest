package com.myorg.operation;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class Step11 implements Operation<String, String> {

	private Parameter<String> param = Parameter.of();

	@Override
	public String execute(String object, StepExecution context) {
		return param.getValue();
	}

	public Parameter<String> getParam() {
		return param;
	}

}
