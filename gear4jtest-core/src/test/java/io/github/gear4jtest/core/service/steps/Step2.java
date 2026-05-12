package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step2 implements Operator<Integer, String> {

    // private Parameter<String> string;

    // public Parameter<String> getA() {
    // return string;
    // }
    @Override
    public String transform(Integer object, StationExecutionContext operationExecution) {
        return "";
    }
}
