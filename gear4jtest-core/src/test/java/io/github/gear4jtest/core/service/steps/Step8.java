package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;

public class Step8 implements Operation<Map<String, String>, Integer> {

	@Override
	public Integer execute(Map<String, String> object, StepExecution context) {
		return 5;
	}

}
