package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.service.SimpleChainBuilderTest;

public class Step12 implements Operation<SimpleChainBuilderTest.Whatever<String>, Integer> {
	@Override
	public Integer execute(SimpleChainBuilderTest.Whatever<String> object, ExecutionContext context) {
		return 2;
	}
}
