package io.github.gear4jtest.studio.demo;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public final class RunPrefixOperation implements Operator<String, String> {
    private final StationParameter<String> label = StationParameter.<String>newBuilder().defaultValue("").build();

    public StationParameter<String> getLabel() {
        return label;
    }

    @Override
    public String transform(String input, StationExecutionContext context) {
        return "RUN:" + label.getValue() + input;
    }
}
