package io.github.gear4jtest.core.model;

import java.util.function.Supplier;

public class Stepa<IN, OUT> {

	private final Supplier<Step<IN, OUT>> step;
	
	public Stepa(Supplier<Step<IN, OUT>> step) {
		this.step = step;
	}
	
}
