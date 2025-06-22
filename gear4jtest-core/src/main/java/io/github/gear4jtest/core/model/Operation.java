package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;

public interface Operation<IN, OUT> {

	OUT execute(IN object, ExecutionContext context);
	
}