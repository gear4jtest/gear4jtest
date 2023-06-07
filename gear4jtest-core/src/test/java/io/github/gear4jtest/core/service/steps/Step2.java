package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class Step2 implements Operation<Integer, String> {

	private Parameter<String> string;
	
	public Parameter<String> getA() {
		return string;
	}
	
	
	@Override
	public String execute(Integer object) {
		return "";
	}

}
