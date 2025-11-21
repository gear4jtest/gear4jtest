package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecution;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.Transformer;

public class Step4 {

	private String whatever;
	
	public Step4(String whatever) {
		this.whatever = whatever;
	}
	
	public class Step4Map implements Transformer<Map<String, String>, Void> {

		@Override
		public Void transform(Map<String, String> object, ExecutionContext context, OperationExecutionContext operationExecution) {
			System.out.println(whatever);
			return null;
		}
		
	}
	
	public class Step4Integer implements Transformer<Integer, Void> {

		@Override
		public Void transform(Integer object, ExecutionContext context, OperationExecutionContext operationExecution) {
			return null;
		}
		
	}

}
