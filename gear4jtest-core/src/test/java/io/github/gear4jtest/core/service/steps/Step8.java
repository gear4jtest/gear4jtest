package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;

public class Step8 implements Operation<Map<String, String>, Integer> {

	@Override
	public Integer execute(Map<String, String> object, ExecutionContext context) {
		return 5;
	}

}
