package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.Operation;

public class Step4 {

	private String whatever;
	
	public Step4(String whatever) {
		this.whatever = whatever;
	}
	
	public class Step4Map implements Operation<Map<String, String>, Void> {

		@Override
		public Void execute(Map<String, String> object) {
			System.out.println(whatever);
			return null;
		}
		
	}
	
	public class Step4Integer implements Operation<Integer, Void> {

		@Override
		public Void execute(Integer object) {
			return null;
		}
		
	}

}
