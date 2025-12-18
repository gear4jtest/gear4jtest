package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step2 implements Operator<Integer, String> {

//	private Parameter<String> string;
	
//	public Parameter<String> getA() {
//		return string;
//	}
	
	
	@Override
	public String transform(Integer object, ExecutionContext context, StationExecutionContext operationExecution) {
		return "";
	}

}
