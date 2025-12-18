package io.github.gear4jtest.core.model;

@FunctionalInterface
public interface Operator<IN, OUT> {

	OUT transform(IN input, ExecutionContext context, StationExecutionContext operationExecution);

}
