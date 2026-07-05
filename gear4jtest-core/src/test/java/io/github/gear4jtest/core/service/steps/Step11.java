package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public class Step11 implements Operator<String, String> {
    private StationParameter<String> param = StationParameter.<String>newBuilder().build();

    @Override
    public String transform(String object, StationExecutionContext operationExecution) {
        return param.getValue();
    }

    public StationParameter<String> getParam() {
        return param;
    }
}
