package io.github.gear4jtest.studio.demo;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public final class PrefixOperation implements Operator<String, String> {
    private final StationParameter<String> prefix = StationParameter.<String>newBuilder().defaultValue("").build();

    public StationParameter<String> getPrefix() {
        return prefix;
    }

    @Override
    public String transform(String input, StationExecutionContext context) {
        return prefix.getValue() + input;
    }
}
