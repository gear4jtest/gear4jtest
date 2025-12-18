package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkerParamsInjector;
import io.github.gear4jtest.core.model.Operator;

public class Step11 implements Operator<String, String> {

	private WorkerParamsInjector.Parameter<String> param = WorkerParamsInjector.Parameter.<String>newBuilder().build();

	@Override
	public String transform(String object, ExecutionContext context, StationExecutionContext operationExecution) {
		return param.getValue();
	}

	public WorkerParamsInjector.Parameter<String> getParam() {
		return param;
	}

}
