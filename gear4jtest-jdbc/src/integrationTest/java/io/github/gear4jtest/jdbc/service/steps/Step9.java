package io.github.gear4jtest.jdbc.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step9 implements Operator<Integer, List<Integer>> {
    @Override
    public List<Integer> transform(Integer integer, StationExecutionContext operationExecution) {
        return Arrays.asList(integer);
    }
}
