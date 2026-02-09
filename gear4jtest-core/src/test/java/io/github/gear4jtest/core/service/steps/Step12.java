package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.service.SimpleChainBuilderTest;

public class Step12 implements Operator<SimpleChainBuilderTest.Whatever<String>, Integer> {
	@Override
	public Integer transform(SimpleChainBuilderTest.Whatever<String> object, StationExecutionContext operationExecution) {
		return 2;
	}
}
