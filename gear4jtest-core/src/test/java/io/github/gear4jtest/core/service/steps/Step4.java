package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.Operator;

public class Step4 {

	private String whatever;
	
	public Step4(String whatever) {
		this.whatever = whatever;
	}
	
	public class Step4Map implements Operator<Map<String, String>, Void> {

		@Override
		public Void transform(Map<String, String> object, ExecutionContext context, StationExecutionContext operationExecution) {
			System.out.println(whatever);
			return null;
		}
		
	}
	
	public class Step4Integer implements Operator<Integer, Void> {

		@Override
		public Void transform(Integer object, ExecutionContext context, StationExecutionContext operationExecution) {
			return null;
		}
		
	}

}
