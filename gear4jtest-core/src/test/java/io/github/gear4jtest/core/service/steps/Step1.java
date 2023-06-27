package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class Step1 implements Operation<String, Integer> {

	private Parameter<String> string = Parameter.of();

	private final Map<String, Object> chainContext = new HashMap<>();

	public Parameter<String> getA() {
		return string;
	}
	
	private String b;
	
	public String getB() {
		return b;
	}
	
	@Override
	public Integer execute(String object, StepExecution context) {
		return 1;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}
	
}
