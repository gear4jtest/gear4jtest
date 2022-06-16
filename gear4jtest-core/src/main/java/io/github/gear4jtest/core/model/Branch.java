package io.github.gear4jtest.core.model;

import java.util.List;

public class Branch<BEGIN, IN> {

	private final List<Object> steps;
	
	public Branch(List<Object> steps) {
		this.steps = steps;
	}
	
}