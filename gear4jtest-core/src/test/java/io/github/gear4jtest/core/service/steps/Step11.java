package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkerParamsInjector;

public class Step11 implements Operator<String, String> {

	private WorkerParamsInjector.Parameter<String> param = WorkerParamsInjector.Parameter.<String>newBuilder().build();

	@Override
	public String transform(String object, StationExecutionContext operationExecution) {
		return param.getValue();
	}

	public WorkerParamsInjector.Parameter<String> getParam() {
		return param;
	}

}
