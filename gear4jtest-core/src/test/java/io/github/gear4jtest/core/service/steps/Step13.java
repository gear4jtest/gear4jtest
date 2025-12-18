package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step13 implements Operator<String, List<String>> {

	@Override
	public List<String> transform(String string, ExecutionContext context, StationExecutionContext operationExecution) {
		return Arrays.asList(string);
	}

}
