package io.github.gear4jtest.core.model;

public interface Operation<IN, OUT> {

	OUT execute(IN object);
	
}