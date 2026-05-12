package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step5 implements Operator<Void, String> {
    @Override
    public String transform(Void object, StationExecutionContext operationExecution) {
        return "b";
    }
}
