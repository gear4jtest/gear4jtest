package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.StepExecution;

@FunctionalInterface
public interface ProcessingOperationProcessor<T> extends BaseProcessor<T, StepExecution> {
	
	default boolean isApplicable(T model) {
		return true;
	}
	
}
