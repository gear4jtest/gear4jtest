package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;

public class Step9 implements Operation<Integer, List<Integer>> {

	@Override
	public List<Integer> execute(Integer integer, StepExecution context) {
		return Arrays.asList(integer);
	}

}
