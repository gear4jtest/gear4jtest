package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector.ChainContext;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class Step6 implements Operation<String, String> {

	protected final Parameter<Integer> value = Parameter.of();
	
	protected final ChainContext chainContext = ChainContext.of();
	
	@Override
	public String execute(String object) {
		return "b";
	}
	
	public Parameter<Integer> getValue() {
		return value;
	}

	public ChainContext getChainContext() {
		return chainContext;
	}

}
