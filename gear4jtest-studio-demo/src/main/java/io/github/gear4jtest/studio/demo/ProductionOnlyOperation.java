package io.github.gear4jtest.studio.demo;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public final class ProductionOnlyOperation implements Operator<String, String> {
    @Override
    public String transform(String input, StationExecutionContext context) {
        throw new IllegalStateException("Must never execute from Studio P0");
    }
}
