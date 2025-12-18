package com.myorg.operation;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step10 implements Operator<Integer, List<String>> {

	@Override
	public List<String> transform(Integer integer, ExecutionContext context, StationExecutionContext operationExecution) {
		return Arrays.asList("");
	}

}
