package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.ParameterValue;

public class Step1 implements Operation<String, Integer> {

	private ParameterValue<String> string = ParameterValue.of();

	private final Map<String, Object> chainContext = new HashMap<>();

	public ParameterValue<String> getA() {
		return string;
	}
	
	private String b;
	
	public String getB() {
		return b;
	}
	
	@Override
	public Integer execute(String object) {
		return 1;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}
	
}
