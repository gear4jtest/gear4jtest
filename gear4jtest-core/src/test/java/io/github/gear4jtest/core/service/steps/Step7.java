package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step7 extends Step6 {
    @Override
    public String transform(String object, StationExecutionContext operationExecution) {
        if (object.equals("a")) {
            throw new RuntimeException();
        }
        return object/* .getValue() */.toString() + "_" + operationExecution.getGlobalContext().get("a", Object.class);
    }
}
