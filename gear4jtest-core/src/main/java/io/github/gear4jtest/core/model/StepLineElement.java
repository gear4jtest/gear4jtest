package io.github.gear4jtest.core.model;

import java.util.List;

import io.github.gear4jtest.core.model.simple.Stepa;
import io.github.gear4jtest.core.model.simple.Stepa.Parameter;

public class StepLineElement extends LineElement {

	private Stepa step;
	
	private List<Parameter> parameters;
	
	public StepLineElement(Stepa step) {
		this.step = step;
		this.parameters = step.getParameters();
	}
	
	public Stepa getStep() {
		return step;
	}
	
}
