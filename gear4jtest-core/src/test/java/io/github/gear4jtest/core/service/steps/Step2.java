package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.ParameterValue;

public class Step2 implements Operation<Integer, String> {

	private ParameterValue<String> string;
	
	public ParameterValue<String> getA() {
		return string;
	}
	
	
	@Override
	public String execute(Integer object) {
		return "";
	}

}
