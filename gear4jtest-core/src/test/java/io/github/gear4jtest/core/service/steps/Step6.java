package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public class Step6 implements Operator<String, String> {
    private final StationParameter<String> param = StationParameter.<String>newBuilder()
            .build();

    @Override
    public String transform(String object, StationExecutionContext operationExecution) {
        return "b";
    }

    public StationParameter<String> getParam() {
        return param;
    }
}
