package io.github.gear4jtest.core.model;

public interface Condition<I> {
    boolean test(I input, ExecutionContext ctx);
}