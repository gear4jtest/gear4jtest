package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

@FunctionalInterface
public interface Operator<IN, OUT> {

    OUT transform(IN input, StationExecutionContext operationExecution);

}
