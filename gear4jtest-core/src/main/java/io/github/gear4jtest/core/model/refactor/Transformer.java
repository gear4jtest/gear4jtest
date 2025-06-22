package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;

@FunctionalInterface
public interface Transformer<IN, OUT> {
	
	OUT transform(IN input, ExecutionContext context);
	
}
