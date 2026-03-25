package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.ExecutionContext;

public interface Condition<I> {
    boolean test(I input, ExecutionContext ctx);
}