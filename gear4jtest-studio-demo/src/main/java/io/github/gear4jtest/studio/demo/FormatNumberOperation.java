package io.github.gear4jtest.studio.demo;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public final class FormatNumberOperation implements Operator<Integer, String> {
    @Override
    public String transform(Integer input, StationExecutionContext context) {
        return Integer.toString(input);
    }
}
