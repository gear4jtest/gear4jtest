package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkerParamsInjector;

public class Step6 implements Operator<String, String> {

//	protected final Parameter<Integer> value = Parameter.of();
	
//	protected final ChainContext chainContext = ChainContext.of();
	private final WorkerParamsInjector.Parameter<String> param = WorkerParamsInjector.Parameter.<String>newBuilder().build();
	
	@Override
	public String transform(String object, StationExecutionContext operationExecution) {
		return "b";
	}

	public WorkerParamsInjector.Parameter<String> getParam() {
		return param;
	}
	
//	public Parameter<Integer> getValue() {
//		return value;
//	}

//	public ChainContext getChainContext() {
//		return chainContext;
//	}

}
