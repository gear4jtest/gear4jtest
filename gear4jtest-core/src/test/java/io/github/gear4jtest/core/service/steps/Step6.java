package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector.ChainContext;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.ParameterValue;

public class Step6 implements Operation<String, String> {

	protected final ParameterValue<Integer> value = ParameterValue.of();
	
	protected final ChainContext chainContext = ChainContext.of();
	
	@Override
	public String execute(String object) {
		return "b";
	}
	
	public ParameterValue<Integer> getValue() {
		return value;
	}

	public ChainContext getChainContext() {
		return chainContext;
	}

}
