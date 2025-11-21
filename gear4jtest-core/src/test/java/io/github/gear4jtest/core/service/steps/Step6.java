package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step6 implements Transformer<String, String> {

//	protected final Parameter<Integer> value = Parameter.of();
	
//	protected final ChainContext chainContext = ChainContext.of();
	
	@Override
	public String transform(String object, ExecutionContext context, OperationExecutionContext operationExecution) {
		return "b";
	}
	
//	public Parameter<Integer> getValue() {
//		return value;
//	}

//	public ChainContext getChainContext() {
//		return chainContext;
//	}

}
