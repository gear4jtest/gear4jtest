package com.myorg.operation;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter;

public class Step11 implements Operator<String, String> {

    private Parameter<String> param = Parameter.<String>newBuilder().build();

    @Override
    public String transform(String object, StationExecutionContext operationExecution) {
        return object + param.getValue();
    }

    public Parameter<String> getParam() {
        return param;
    }
}
