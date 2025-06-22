package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector.Parameter;

public class Step3 implements Operation<String, Map<String, String>> {

	private Parameter<String> param = Parameter.of();

	@Override
	public Map<String, String> execute(String object, ExecutionContext context) {
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
