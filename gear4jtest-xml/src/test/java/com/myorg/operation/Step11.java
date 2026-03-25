package com.myorg.operation;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter;
import io.github.gear4jtest.core.api.behavior.Operator;

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
