package io.github.gear4jtest.core.model;

public interface Step<IN, OUT> {

	OUT execute(IN object);
	
}