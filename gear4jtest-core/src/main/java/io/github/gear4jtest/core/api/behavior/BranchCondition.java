package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.ExecutionContext;

@FunctionalInterface
public interface BranchCondition<I> {
    boolean test(I input, ExecutionContext ctx, SiblingBranchOutcomes siblings);
}
