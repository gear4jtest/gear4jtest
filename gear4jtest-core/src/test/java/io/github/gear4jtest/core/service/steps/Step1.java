package io.github.gear4jtest.core.service.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step1 implements Operator<String, Integer> {

    // private Parameter<String> string = Parameter.of();
    private final Map<String, Object> chainContext = new HashMap<>();

    // public Parameter<String> getA() {
    // return string;
    // }
    private String b;

    public String getB() {
        return b;
    }

    @Override
    public Integer transform(String object, StationExecutionContext operationExecution) {
        return 1;
    }

    public Map<String, Object> getChainContext() {
        return chainContext;
    }
}
