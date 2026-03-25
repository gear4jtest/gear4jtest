package com.myorg.operation;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.behavior.Operator;

public class Step10 implements Operator<Integer, List<String>> {

	@Override
	public List<String> transform(Integer integer, ExecutionContext context, StationExecutionContext operationExecution) {
		return Arrays.asList("");
	}

}
