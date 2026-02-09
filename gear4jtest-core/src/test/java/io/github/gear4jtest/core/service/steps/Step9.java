package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;

public class Step9 implements Operator<Integer, List<Integer>> {

	@Override
	public List<Integer> transform(Integer integer, StationExecutionContext operationExecution) {
		return Arrays.asList(integer);
	}

}
