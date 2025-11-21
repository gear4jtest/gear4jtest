package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step9 implements Transformer<Integer, List<Integer>> {

	@Override
	public List<Integer> transform(Integer integer, ExecutionContext context, OperationExecutionContext operationExecution) {
		return Arrays.asList(integer);
	}

}
