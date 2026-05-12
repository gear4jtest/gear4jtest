package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step13 implements Operator<String, List<String>> {

    @Override
    public List<String> transform(String string, StationExecutionContext operationExecution) {
        return Arrays.asList(string);
    }

}
