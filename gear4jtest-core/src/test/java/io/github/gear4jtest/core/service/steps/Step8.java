package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step8 implements Operator<Map<String, String>, Integer> {

	@Override
	public Integer transform(Map<String, String> object, StationExecutionContext operationExecution) {
		return 5;
	}

}
