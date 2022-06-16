package io.github.gear4j.steps;

import java.util.Map;

import io.github.gear4jtest.core.model.Step;

public class Step4 {

	private String whatever;
	
	public Step4(String whatever) {
		this.whatever = whatever;
	}
	
	public class Step4Map implements Step<Map<String, String>, Void> {

		@Override
		public Void execute(Map<String, String> object) {
			System.out.println(whatever);
			return null;
		}
		
	}
	
	public class Step4Integer implements Step<Integer, Void> {

		@Override
		public Void execute(Integer object) {
			return null;
		}
		
	}

}
