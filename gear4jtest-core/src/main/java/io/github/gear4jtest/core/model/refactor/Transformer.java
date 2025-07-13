package io.github.gear4jtest.core.model.refactor;

@FunctionalInterface
public interface Transformer<IN, OUT> {
	
	OUT transform(IN input, ExecutionContext context, OperationExecution operationExecution);
	
}
