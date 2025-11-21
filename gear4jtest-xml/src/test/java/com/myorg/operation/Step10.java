package com.myorg.operation;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step10 implements Transformer<Integer, List<String>> {

	@Override
	public List<String> transform(Integer integer, ExecutionContext context, OperationExecutionContext operationExecution) {
		return Arrays.asList("");
	}

}
