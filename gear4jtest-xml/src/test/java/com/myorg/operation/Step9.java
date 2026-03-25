package com.myorg.operation;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.behavior.Operator;

public class Step9 implements Operator<Integer, List<Integer>> {

	@Override
	public List<Integer> transform(Integer integer, ExecutionContext context, StationExecutionContext operationExecution) {
		return Arrays.asList(integer);
	}

}
