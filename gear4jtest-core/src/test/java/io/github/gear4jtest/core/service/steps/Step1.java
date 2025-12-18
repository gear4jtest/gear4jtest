package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step1 implements Operator<String, Integer> {

//	private Parameter<String> string = Parameter.of();

	private final Map<String, Object> chainContext = new HashMap<>();

//	public Parameter<String> getA() {
//		return string;
//	}

	private String b;
	
	public String getB() {
		return b;
	}
	
	@Override
	public Integer transform(String object, ExecutionContext context, StationExecutionContext operationExecution) {
		return 1;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}
	
}
