package com.myorg.operation;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public class Step3 implements Operator<String, Map<String, String>> {
    private StationParameter<String> param = StationParameter.<String>newBuilder().build();

    @Override
    public Map<String, String> transform(String object, StationExecutionContext operationExecution) {
        if (object.equals("a")) {
            throw new RuntimeException();
        }
        Map<String, String> map = new HashMap<>();
        map.put(object, param.getValue());
        return map;
    }

    public StationParameter<String> getParam() {
        return param;
    }
}
