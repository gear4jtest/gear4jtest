package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;

public class Step10 implements Operator<Integer, List<String>> {

	@Override
	public List<String> transform(Integer integer, StationExecutionContext operationExecution) {
		return Arrays.asList("");
	}

}
