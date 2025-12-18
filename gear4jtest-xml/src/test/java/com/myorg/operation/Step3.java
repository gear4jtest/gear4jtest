package com.myorg.operation;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkerParamsInjector.Parameter;
import io.github.gear4jtest.core.model.Operator;

public class Step3 implements Operator<String, Map<String, String>> {

	private Parameter<String> param = Parameter.<String>newBuilder().build();

	@Override
	public Map<String, String> transform(String object, ExecutionContext context, StationExecutionContext operationExecution) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		Map<String, String> map = new HashMap<>();
		map.put(object, object);
		return map;
	}

	public Parameter<String> getParam() {
		return param;
	}

}
