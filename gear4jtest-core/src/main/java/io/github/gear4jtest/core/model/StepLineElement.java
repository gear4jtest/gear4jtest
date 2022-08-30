package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.model.simple.Stepa;

public class StepLineElement extends LineElement {

	private Stepa step;
	
	public StepLineElement(Stepa step) {
		this.step = step;
	}
	
	public Stepa getStep() {
		return step;
	}
	
}
