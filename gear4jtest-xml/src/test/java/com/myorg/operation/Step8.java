package com.myorg.operation;

import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step8 implements Operator<Map<String, String>, Integer> {
    @Override
    public Integer transform(Map<String, String> object, StationExecutionContext operationExecution) {
        return object.size();
    }
}
