package io.github.gear4jtest.studio.demo;

import java.util.Locale;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public final class UppercaseOperation implements Operator<String, String> {
    @Override
    public String transform(String input, StationExecutionContext context) {
        return input.toUpperCase(Locale.ROOT);
    }
}
