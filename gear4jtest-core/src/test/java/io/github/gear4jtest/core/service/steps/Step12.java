package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.Transformer;
import io.github.gear4jtest.core.service.SimpleChainBuilderTest;

public class Step12 implements Transformer<SimpleChainBuilderTest.Whatever<String>, Integer> {
	@Override
	public Integer transform(SimpleChainBuilderTest.Whatever<String> object, ExecutionContext context, OperationExecution operationExecution) {
		return 2;
	}
}
