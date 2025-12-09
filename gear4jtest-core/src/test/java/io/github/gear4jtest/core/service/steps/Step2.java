package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step2 implements Transformer<Integer, String> {

//	private Parameter<String> string;
	
//	public Parameter<String> getA() {
//		return string;
//	}
	
	
	@Override
	public String transform(Integer object, ExecutionContext context, OperationExecutionContext operationExecution) {
		return "";
	}

}
