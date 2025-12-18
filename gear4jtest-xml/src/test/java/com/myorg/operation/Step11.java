package com.myorg.operation;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkerParamsInjector.Parameter;
import io.github.gear4jtest.core.model.Operator;

public class Step11 implements Operator<String, String> {

	private Parameter<String> param = Parameter.<String>newBuilder().build();

	@Override
	public String transform(String object, ExecutionContext context, StationExecutionContext operationExecution) {
		return param.getValue();
	}

	public Parameter<String> getParam() {
		return param;
	}

}
