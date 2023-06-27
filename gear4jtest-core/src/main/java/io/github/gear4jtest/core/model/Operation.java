package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.context.StepExecution;

public interface Operation<IN, OUT> {

	OUT execute(IN object, StepExecution context);
	
}