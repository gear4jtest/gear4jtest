package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;

public class Step3 implements Operation<String, Map<String, String>> {

	@Override
	public Map<String, String> execute(String object, StepExecution context) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		return new HashMap<>();
	}

}
