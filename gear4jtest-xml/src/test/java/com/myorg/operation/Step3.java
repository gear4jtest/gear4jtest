package com.myorg.operation;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector.Parameter;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step3 implements Transformer<String, Map<String, String>> {

	private Parameter<String> param = Parameter.of();

	@Override
	public Map<String, String> transform(String object, ExecutionContext context, OperationExecution operationExecution) {
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
