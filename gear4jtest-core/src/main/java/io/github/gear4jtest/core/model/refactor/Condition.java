package io.github.gear4jtest.core.model.refactor;

public interface Condition<I> {
    boolean test(I input, ExecutionContext ctx);
}